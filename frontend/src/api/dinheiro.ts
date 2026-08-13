/**
 * Dinheiro nunca vira `number` nesta aplicacao.
 *
 * A API manda "374.75" como string de proposito: `Number` em JavaScript e
 * IEEE-754 e nao representa todo decimal exatamente. Um `parseFloat` aqui
 * jogaria fora a decisao tomada no backend.
 *
 * Toda conta e feita em centavos inteiros e o resultado volta para string.
 */

export type Dinheiro = string;

export function paraCentavos(valor: Dinheiro): number {
  const [inteiros, decimais = ''] = valor.trim().split('.');
  const centavos = (decimais + '00').slice(0, 2);
  const negativo = inteiros.startsWith('-');
  const magnitude = Number(inteiros.replace('-', '')) * 100 + Number(centavos);

  return negativo ? -magnitude : magnitude;
}

export function deCentavos(centavos: number): Dinheiro {
  const negativo = centavos < 0;
  const absoluto = Math.abs(centavos);

  return `${negativo ? '-' : ''}${Math.floor(absoluto / 100)}.${String(absoluto % 100).padStart(2, '0')}`;
}

/** R$ 1.234,56 — separadores brasileiros, sem passar por ponto flutuante. */
export function formatar(valor: Dinheiro): string {
  const centavos = paraCentavos(valor);
  const negativo = centavos < 0;
  const absoluto = Math.abs(centavos);

  const reais = String(Math.floor(absoluto / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const resto = String(absoluto % 100).padStart(2, '0');

  return `${negativo ? '-' : ''}R$ ${reais},${resto}`;
}

/** Converte o que o usuario digitou ("1.234,56" ou "1234,56") para o formato da API. */
export function doCampo(entrada: string): Dinheiro {
  const limpo = entrada.replace(/\./g, '').replace(',', '.').trim();
  const [inteiros = '0', decimais = ''] = limpo.split('.');

  return `${inteiros || '0'}.${(decimais + '00').slice(0, 2)}`;
}

export function ehPositivo(valor: Dinheiro): boolean {
  return paraCentavos(valor) > 0;
}
