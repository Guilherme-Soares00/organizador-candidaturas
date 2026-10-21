import { formatDate, safeLink, stageLabel } from './helpers.mjs';

const $ = selector => document.querySelector(selector);
const form = $('#form');
const editor = $('#editor');
const deleteDialog = $('#delete-dialog');
const stages = ['ENVIADA', 'ENTREVISTA', 'PROPOSTA', 'ENCERRADA'];
let editingId = null;
let deletingItem = null;
let requestVersion = 0;
let debounce;
let toastTimer;
let saving = false;
let deleting = false;

function localToday() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

async function api(path, options = {}) {
  let response;
  try {
    response = await fetch(path, {
      ...options,
      headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}) }
    });
  } catch {
    throw new Error('Não foi possível conectar. Verifique se a aplicação está em execução.');
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    const details = Array.isArray(error.erros) ? error.erros.map(item => typeof item === 'string' ? item : item.mensagem || item.message || '').filter(Boolean).join(' ') : '';
    throw new Error(details || error.mensagem || 'Não foi possível concluir a operação. Tente novamente.');
  }
  return response.status === 204 ? null : response.json();
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function notify(message) {
  clearTimeout(toastTimer);
  $('#toast').textContent = message;
  $('#toast').hidden = false;
  toastTimer = setTimeout(() => { $('#toast').hidden = true; }, 4000);
}

function openEditor(item = null) {
  form.reset();
  editingId = item?.id ?? null;
  $('#editor-title').textContent = item ? 'Editar candidatura' : 'Nova candidatura';
  $('#form-error').hidden = true;
  $('#data').max = localToday();
  if (item) {
    for (const name of ['empresa', 'cargo', 'link', 'dataCandidatura', 'etapa', 'observacoes']) {
      form.elements.namedItem(name).value = item[name] ?? '';
    }
  } else {
    $('#data').value = localToday();
  }
  editor.showModal();
  $('#empresa').focus();
}

function renderCard(item) {
  const article = element('article');
  const content = element('div');
  content.append(element('span', 'company', item.empresa), element('h3', '', item.cargo));
  const meta = element('div', 'meta');
  meta.append(element('span', `stage ${item.etapa}`, stageLabel(item.etapa)));
  const date = element('time', '', formatDate(item.dataCandidatura));
  date.dateTime = item.dataCandidatura;
  meta.append(date);
  const link = safeLink(item.link);
  if (link) {
    const anchor = element('a', '', 'Ver vaga ↗');
    anchor.href = link;
    anchor.target = '_blank';
    anchor.rel = 'noopener noreferrer';
    meta.append(anchor);
  }
  content.append(meta);
  if (item.observacoes) content.append(element('p', 'notes', item.observacoes));
  const actions = element('div', 'card-actions');
  const edit = element('button', '', 'Editar');
  edit.type = 'button';
  edit.addEventListener('click', () => openEditor(item));
  const remove = element('button', '', 'Excluir');
  remove.type = 'button';
  remove.addEventListener('click', () => {
    deletingItem = item;
    $('#delete-description').textContent = `${item.cargo} na empresa ${item.empresa} será removida da sua lista.`;
    $('#delete-error').hidden = true;
    deleteDialog.showModal();
    $('#cancel-delete').focus();
  });
  actions.append(edit, remove);
  article.append(content, actions);
  return article;
}

async function load() {
  const version = ++requestVersion;
  const params = new URLSearchParams();
  const busca = $('#search').value.trim();
  const etapa = $('#filter').value;
  if (busca) params.set('busca', busca);
  if (etapa) params.set('etapa', etapa);
  $('#clear').hidden = !(busca || etapa);
  $('#list').setAttribute('aria-busy', 'true');
  $('#loading').hidden = false;
  $('#load-error').hidden = true;
  $('#empty').hidden = true;
  $('#list').replaceChildren();
  try {
    const [items, summary] = await Promise.all([api(`/api/candidaturas?${params}`), api('/api/resumo')]);
    if (version !== requestVersion) return;
    $('#total').textContent = summary.total;
    for (const stage of stages) $(`#count-${stage}`).textContent = summary.porEtapa[stage] ?? 0;
    $('#result-count').textContent = items.length;
    $('#list').replaceChildren(...items.map(renderCard));
    $('#empty').hidden = items.length !== 0;
    $('#empty-title').textContent = busca || etapa ? 'Nenhuma candidatura encontrada' : 'Seu próximo passo começa aqui';
    $('#empty-text').textContent = busca || etapa ? 'Tente outro termo ou limpe os filtros para ver suas candidaturas.' : 'Cadastre uma vaga para acompanhar sua primeira candidatura.';
    $('#empty-new').hidden = Boolean(busca || etapa);
  } catch (error) {
    if (version !== requestVersion) return;
    $('#load-error-text').textContent = error.message;
    $('#load-error').hidden = false;
    $('#result-count').textContent = '—';
  } finally {
    if (version === requestVersion) {
      $('#loading').hidden = true;
      $('#list').setAttribute('aria-busy', 'false');
    }
  }
}

for (const id of ['#new', '#empty-new']) $(id).addEventListener('click', () => openEditor());
for (const id of ['#close-editor', '#cancel-editor']) $(id).addEventListener('click', () => { if (!saving) editor.close(); });
editor.addEventListener('cancel', event => { if (saving) event.preventDefault(); });
$('#cancel-delete').addEventListener('click', () => { if (!deleting) deleteDialog.close(); });
deleteDialog.addEventListener('cancel', event => { if (deleting) event.preventDefault(); });
$('#search').addEventListener('input', () => {
  ++requestVersion;
  clearTimeout(debounce);
  debounce = setTimeout(load, 250);
});
$('#filter').addEventListener('change', () => { clearTimeout(debounce); load(); });
$('#clear').addEventListener('click', () => {
  clearTimeout(debounce);
  $('#search').value = '';
  $('#filter').value = '';
  load();
});
$('#retry').addEventListener('click', load);

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (saving) return;
  $('#form-error').hidden = true;
  const payload = Object.fromEntries(new FormData(form));
  for (const key of ['empresa', 'cargo', 'link', 'observacoes']) payload[key] = payload[key].trim();
  if (!payload.empresa || !payload.cargo) {
    $('#form-error').textContent = 'Preencha a empresa e o cargo.';
    $('#form-error').hidden = false;
    return;
  }
  if (payload.link && !safeLink(payload.link)) {
    $('#form-error').textContent = 'O link da vaga deve começar com http:// ou https://.';
    $('#form-error').hidden = false;
    return;
  }
  saving = true;
  $('#save').disabled = true;
  $('#save').textContent = 'Salvando…';
  try {
    const editing = editingId !== null;
    await api(editing ? `/api/candidaturas/${editingId}` : '/api/candidaturas', { method: editing ? 'PUT' : 'POST', body: JSON.stringify(payload) });
    editor.close();
    notify(editing ? 'Candidatura atualizada.' : 'Candidatura cadastrada. Boa sorte!');
    await load();
  } catch (error) {
    $('#form-error').textContent = error.message;
    $('#form-error').hidden = false;
  } finally {
    saving = false;
    $('#save').disabled = false;
    $('#save').textContent = 'Salvar candidatura';
  }
});

$('#confirm-delete').addEventListener('click', async () => {
  if (deleting || !deletingItem) return;
  deleting = true;
  $('#confirm-delete').disabled = true;
  $('#delete-error').hidden = true;
  try {
    await api(`/api/candidaturas/${deletingItem.id}`, { method: 'DELETE' });
    deleteDialog.close();
    notify('Candidatura excluída.');
    deletingItem = null;
    await load();
  } catch (error) {
    $('#delete-error').textContent = error.message;
    $('#delete-error').hidden = false;
  } finally {
    deleting = false;
    $('#confirm-delete').disabled = false;
  }
});

load();
