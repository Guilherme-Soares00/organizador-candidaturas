const { test, expect } = require('@playwright/test');

test('oferece todas as etapas e filtra candidaturas enviadas', async ({ page, request }) => {
  await page.goto('/');
  const filter = page.getByLabel('Filtrar por etapa');
  await expect(filter.locator('option')).toHaveText(['Todas as etapas', 'Enviada', 'Entrevista', 'Proposta', 'Encerrada']);
  const created = [];
  const empresa = `Filtro E2E ${Date.now()}`;
  try {
    for (const etapa of ['ENVIADA', 'ENTREVISTA']) {
      const response = await request.post('/api/candidaturas', {
        data: { empresa, cargo: 'Desenvolvedor Java', dataCandidatura: '2025-01-10', etapa }
      });
      expect(response.status()).toBe(201);
      created.push((await response.json()).id);
    }
    await page.getByLabel('Buscar candidaturas').fill(empresa);
    await expect(page.getByRole('article')).toHaveCount(2);
    await filter.selectOption('ENVIADA');
    await expect(filter).toHaveValue('ENVIADA');
    await expect(page.getByRole('article')).toHaveCount(1);
    await expect(page.getByRole('article').locator('.stage')).toHaveText('Enviada');
  } finally {
    for (const id of created) await request.delete(`/api/candidaturas/${id}`);
  }
});
