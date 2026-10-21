export function formatDate(value) {
  return value.split('-').reverse().join('/');
}

export function safeLink(value) {
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

export function stageLabel(value) {
  return { ENVIADA: 'Enviada', ENTREVISTA: 'Entrevista', PROPOSTA: 'Proposta', ENCERRADA: 'Encerrada' }[value] ?? value;
}
