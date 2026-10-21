import test from 'node:test';
import assert from 'node:assert/strict';
import { formatDate, safeLink, stageLabel } from '../src/main/resources/static/helpers.mjs';

test('formata data sem deslocamento de fuso', () => {
  assert.equal(formatDate('2026-10-21'), '21/10/2026');
});

test('aceita somente links web seguros', () => {
  assert.equal(safeLink('https://example.com/vaga'), 'https://example.com/vaga');
  assert.equal(safeLink('javascript:alert(1)'), null);
  assert.equal(safeLink(''), null);
  assert.equal(safeLink('ftp://example.com'), null);
});

test('traduz etapas conhecidas sem esconder valores desconhecidos', () => {
  assert.equal(stageLabel('ENTREVISTA'), 'Entrevista');
  assert.equal(stageLabel('ENVIADA'), 'Enviada');
  assert.equal(stageLabel('OUTRA'), 'OUTRA');
});
