const { test, expect } = require('@playwright/test');

test('cadastra, filtra, edita e remove uma candidatura', async ({ page }) => {
  const company = `Empresa E2E ${Date.now()}`;
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Suas próximas oportunidades' })).toBeVisible();
  await page.getByRole('button', { name: 'Nova candidatura', exact: true }).click();
  await page.getByLabel('Empresa', { exact: true }).fill(company);
  await page.getByLabel('Cargo', { exact: true }).fill('Desenvolvedor Java Júnior');
  await page.getByLabel('Link da vaga').fill('https://example.com/vaga');
  await page.getByLabel('Observações').fill('<script>alert(1)</script>');
  await page.getByRole('button', { name: 'Salvar candidatura' }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByLabel('Buscar candidaturas').fill(company);
  const card = page.getByRole('article').filter({ hasText: company });
  await expect(card).toHaveCount(1);
  await expect(card.getByText('<script>alert(1)</script>', { exact: true })).toBeVisible();
  await card.getByRole('button', { name: 'Editar' }).click();
  await page.getByLabel('Etapa', { exact: true }).selectOption('ENTREVISTA');
  await page.getByRole('button', { name: 'Salvar candidatura' }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByLabel('Filtrar por etapa').selectOption('PROPOSTA');
  await expect(page.getByRole('article')).toHaveCount(0);
  await expect(page.getByText('Nenhuma candidatura encontrada')).toBeVisible();
  await page.getByLabel('Filtrar por etapa').selectOption('ENTREVISTA');
  await expect(card).toHaveCount(1);
  await page.reload();
  await page.getByLabel('Buscar candidaturas').fill(company);
  await expect(card).toHaveCount(1);
  await card.getByRole('button', { name: 'Excluir' }).click();
  await page.getByRole('button', { name: 'Excluir candidatura', exact: true }).click();
  await expect(card).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('formulário e filtros cabem na tela do celular', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await page.getByRole('button', { name: 'Nova candidatura', exact: true }).click();
  await expect(page.getByLabel('Empresa', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
});
