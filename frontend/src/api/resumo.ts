/**
 * Leitura do extrato: o que a tela precisa contar sobre o dinheiro.
 *
 * Tudo aqui e funcao pura sobre as linhas que a API ja devolve -- nenhuma
 * chamada nova, nenhum endpoint novo. O extrato do mes cabe numa pagina de
 * 100 linhas, entao somar no cliente sai mais barato que inventar um endpoint
 * de resumo que teria de repetir, em SQL, a mesma aritmetica.
 *
 * O limite disso e honesto e vale registrar: quem tiver mais de 100
 * lancamentos no periodo veria um resumo truncado. Nesse dia o calculo desce
 * para o banco, onde ele ja deveria estar em escala -- somar dinheiro em
 * JavaScript e conveniencia de demonstracao, nao arquitetura.
 *
 * Centavos inteiros do inicio ao fim, como no resto do app: `Number` nao
 * representa 0,10 exatamente e uma soma de sessenta parcelas acumula o erro.
 */

import type { LinhaDoExtrato } from './cifra';
import { deCentavos, paraCentavos, type Dinheiro } from './dinheiro';

const DIA = 86_400_000;

export type Semana = {
  /** Segunda-feira que abre a semana. */
  inicio: Date;
  rotulo: string;
  entradas: number;
  saidas: number;
};

export type Destino = {
  nome: string;
  centavos: number;
  /** Fracao do maior destino, 0 a 1 -- e o que desenha a barra. */
  fatia: number;
};

export type ResumoDoMes = {
  mes: string;
  entradas: Dinheiro;
  saidas: Dinheiro;
  resultado: Dinheiro;
  movimentos: number;
};

/** Segunda-feira da semana da data, com o relogio zerado. */
function inicioDaSemana(data: Date): Date {
  const dia = new Date(data.getFullYear(), data.getMonth(), data.getDate());
  // getDay() devolve 0 para domingo; aqui a semana abre na segunda.
  const desdeSegunda = (dia.getDay() + 6) % 7;

  return new Date(dia.getTime() - desdeSegunda * DIA);
}

/**
 * Entradas e saidas das ultimas `quantidade` semanas, da mais antiga para a
 * mais recente.
 *
 * Semana sem movimento entra na lista com zero em vez de sumir: buraco no eixo
 * do tempo mente sobre o ritmo dos gastos -- duas barras vizinhas pareceriam
 * semanas seguidas quando ha um mes entre elas.
 */
export function porSemana(linhas: LinhaDoExtrato[], quantidade = 8, hoje = new Date()): Semana[] {
  const primeira = inicioDaSemana(hoje).getTime() - (quantidade - 1) * 7 * DIA;

  const semanas: Semana[] = Array.from({ length: quantidade }, (_, i) => {
    const inicio = new Date(primeira + i * 7 * DIA);

    return {
      inicio,
      rotulo: inicio.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }),
      entradas: 0,
      saidas: 0,
    };
  });

  for (const linha of linhas) {
    const indice = Math.floor((inicioDaSemana(new Date(linha.data)).getTime() - primeira) / (7 * DIA));
    if (indice < 0 || indice >= semanas.length) continue;

    const centavos = Math.abs(paraCentavos(linha.valor));
    if (linha.sentido === 'CREDITO') semanas[indice].entradas += centavos;
    else semanas[indice].saidas += centavos;
  }

  return semanas;
}

/**
 * Para onde o dinheiro foi: saidas do periodo somadas por descricao.
 *
 * Agrupar pela descricao que o proprio lancamento carrega, e nao por uma
 * tabela de categorias inventada aqui, e a diferenca entre relatar e adivinhar.
 * Categoria fixa exigiria classificar "Mercado" e "Farmacia" por um dicionario
 * que erra no primeiro estabelecimento que ninguem previu -- e um extrato que
 * inventa rotulo perde a autoridade que o razao passou o projeto inteiro
 * construindo.
 */
export function paraOndeFoi(linhas: LinhaDoExtrato[], desde: Date, quantos = 5): Destino[] {
  const soma = new Map<string, number>();

  for (const linha of linhas) {
    if (linha.sentido !== 'DEBITO' || new Date(linha.data) < desde) continue;

    const nome = linha.contraparte ?? linha.descricao ?? rotuloDoTipo(linha.tipo);
    soma.set(nome, (soma.get(nome) ?? 0) + Math.abs(paraCentavos(linha.valor)));
  }

  const ordenados = [...soma.entries()].sort(([, a], [, b]) => b - a).slice(0, quantos);
  const maior = ordenados[0]?.[1] ?? 1;

  return ordenados.map(([nome, centavos]) => ({ nome, centavos, fatia: centavos / maior }));
}

export function resumoDoMes(linhas: LinhaDoExtrato[], hoje = new Date()): ResumoDoMes {
  const primeiroDia = new Date(hoje.getFullYear(), hoje.getMonth(), 1);

  let entradas = 0;
  let saidas = 0;
  let movimentos = 0;

  for (const linha of linhas) {
    if (new Date(linha.data) < primeiroDia) continue;

    const centavos = Math.abs(paraCentavos(linha.valor));
    if (linha.sentido === 'CREDITO') entradas += centavos;
    else saidas += centavos;
    movimentos += 1;
  }

  return {
    mes: hoje.toLocaleDateString('pt-BR', { month: 'long' }),
    entradas: deCentavos(entradas),
    saidas: deCentavos(saidas),
    resultado: deCentavos(entradas - saidas),
    movimentos,
  };
}

/** "PIX_ENVIADO" nao e texto de tela; vira "Pix enviado". */
export function rotuloDoTipo(tipo: string): string {
  const palavras = tipo.toLowerCase().replace(/_/g, ' ');

  return palavras.charAt(0).toUpperCase() + palavras.slice(1);
}

/** R$ 12,4 mil -- so para eixo de grafico, onde o centavo nao cabe nem importa. */
export function compacto(centavos: number): string {
  if (centavos === 0) return '0';
  if (centavos < 100_000) return `${Math.round(centavos / 100)}`;

  const milhares = centavos / 100_000;
  const texto = milhares.toFixed(milhares < 10 ? 1 : 0);

  // "2 mil" e nao "2,0 mil": a casa decimal so aparece quando diz alguma coisa.
  return `${texto.replace(/[.,]0$/, '').replace('.', ',')} mil`;
}
