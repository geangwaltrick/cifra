/**
 * Entradas e saidas das ultimas oito semanas.
 *
 * SVG escrito a mao, sem biblioteca de grafico: sao dezesseis retangulos e
 * quatro linhas de grade. Recharts custaria uns 100 KB comprimidos para
 * desenhar isso, mais que o resto do app somado.
 *
 * As duas series nao se distinguem so pela cor. Entrada e saida ficam lado a
 * lado em posicao fixa (entrada sempre a esquerda), tem legenda, tem rotulo no
 * hover e existem tambem como tabela para leitor de tela. Vermelho e verde sao
 * justamente o par que some na deuteranopia -- por isso o verde puxa para o
 * azul-esverdeado, e a separacao das duas cores foi medida em OKLab nos dois
 * temas em vez de escolhida no olho.
 */

import { useId, useState } from 'react';
import { deCentavos, formatar } from '../api/dinheiro';
import { compacto, type Semana } from '../api/resumo';

const LARGURA = 560;
const ALTURA = 200;
const MARGEM = { topo: 18, direita: 10, base: 28, esquerda: 46 };

const CHAO = ALTURA - MARGEM.base;
const AREA_LARGURA = LARGURA - MARGEM.esquerda - MARGEM.direita;
const AREA_ALTURA = CHAO - MARGEM.topo;

const BARRA = 13;
const VAO = 2;

/** Retangulo com os dois cantos de cima arredondados e a base assentada na linha do zero. */
function barra(x: number, altura: number, raio = 4): string {
  const r = Math.min(raio, altura, BARRA / 2);
  const topo = CHAO - altura;

  return `M${x} ${CHAO} L${x} ${topo + r} Q${x} ${topo} ${x + r} ${topo}
          L${x + BARRA - r} ${topo} Q${x + BARRA} ${topo} ${x + BARRA} ${topo + r}
          L${x + BARRA} ${CHAO} Z`;
}

/** Teto do eixo em 1, 2 ou 5 vezes uma potencia de dez -- escala que se le, e nao 3.847. */
function teto(valor: number): number {
  if (valor <= 0) return 1;

  const potencia = 10 ** Math.floor(Math.log10(valor));
  const passo = [1, 2, 2.5, 5, 10].find((p) => p * potencia >= valor) ?? 10;

  return passo * potencia;
}

export function FluxoSemanal({ semanas }: { semanas: Semana[] }) {
  const [ativa, setAtiva] = useState<number | null>(null);
  const tabelaId = useId();

  const maximo = teto(Math.max(...semanas.flatMap((s) => [s.entradas, s.saidas]), 1));
  const faixa = AREA_LARGURA / semanas.length;
  const altura = (centavos: number) => (centavos / maximo) * AREA_ALTURA;
  const centro = (i: number) => MARGEM.esquerda + faixa * (i + 0.5);

  // O maior gasto do periodo e o unico numero escrito direto no desenho. Valor
  // em cima de toda barra vira ruido e some com a leitura da forma, que e o
  // que um grafico faz melhor que uma tabela.
  const pico = semanas.reduce((maior, s, i) => (s.saidas > semanas[maior].saidas ? i : maior), 0);
  const semana = ativa === null ? null : semanas[ativa];

  return (
    <div className="gr-caixa">
      <div className="gr-legenda">
        <span className="gr-chave">
          <i className="gr-amostra entrada" aria-hidden="true" /> Entradas
        </span>
        <span className="gr-chave">
          <i className="gr-amostra saida" aria-hidden="true" /> Saídas
        </span>
      </div>

      <div className="gr-tela">
        <svg
          viewBox={`0 0 ${LARGURA} ${ALTURA}`}
          className="gr-svg"
          role="img"
          aria-labelledby={tabelaId}
          onMouseLeave={() => setAtiva(null)}
        >
          {/* Grade recessiva: tres referencias bastam para estimar altura. */}
          {[0, 0.5, 1].map((fracao) => {
            const y = CHAO - fracao * AREA_ALTURA;

            return (
              <g key={fracao}>
                <line x1={MARGEM.esquerda} y1={y} x2={LARGURA - MARGEM.direita} y2={y} className="gr-grade" />
                <text x={MARGEM.esquerda - 8} y={y + 3.5} textAnchor="end" className="gr-eixo">
                  {compacto(fracao * maximo)}
                </text>
              </g>
            );
          })}

          {semanas.map((s, i) => (
            <g key={s.rotulo}>
              <path d={barra(centro(i) - BARRA - VAO / 2, altura(s.entradas))} className="gr-barra entrada" />
              <path d={barra(centro(i) + VAO / 2, altura(s.saidas))} className="gr-barra saida" />

              {/* Num celular o eixo tem uns 40 px por semana: oito datas viram
                  uma tarja cinza. As de indice impar somem por CSS na largura
                  pequena, e o eixo continua legivel com metade das marcas. */}
              <text
                x={centro(i)}
                y={ALTURA - 10}
                textAnchor="middle"
                className={i % 2 === 0 ? 'gr-eixo' : 'gr-eixo alternado'}
              >
                {s.rotulo}
              </text>

              {/* Alvo de hover da largura inteira da faixa: mira no espaco
                  entre as barras funciona igual, e no celular o dedo acerta. */}
              <rect
                x={MARGEM.esquerda + faixa * i}
                y={MARGEM.topo - 8}
                width={faixa}
                height={AREA_ALTURA + 8}
                fill="transparent"
                onMouseEnter={() => setAtiva(i)}
              />
            </g>
          ))}

          {/* O rotulo sobe acima da barra mais alta da semana, e nao da barra
              que ele descreve: encostado no topo da saida, ele cairia no meio
              da entrada vizinha quando esta fosse maior. */}
          {semanas[pico].saidas > 0 && ativa === null && (
            <text
              x={centro(pico) + VAO / 2 + BARRA / 2}
              y={CHAO - altura(Math.max(semanas[pico].saidas, semanas[pico].entradas)) - 8}
              textAnchor="middle"
              className="gr-marca-valor"
            >
              {formatar(deCentavos(semanas[pico].saidas))}
            </text>
          )}
        </svg>

        {semana && ativa !== null && (
          <div className="gr-dica" style={{ left: `${(centro(ativa) / LARGURA) * 100}%` }} aria-hidden="true">
            <span className="gr-dica-titulo">semana de {semana.rotulo}</span>
            <span className="gr-dica-linha">
              <i className="gr-amostra entrada" /> {formatar(deCentavos(semana.entradas))}
            </span>
            <span className="gr-dica-linha">
              <i className="gr-amostra saida" /> {formatar(deCentavos(semana.saidas))}
            </span>
          </div>
        )}
      </div>

      {/* Mesmo dado em tabela: quem usa leitor de tela le os numeros, nao o desenho. */}
      <table id={tabelaId} className="apenas-leitor">
        <caption>Entradas e saídas por semana, nas últimas {semanas.length} semanas</caption>
        <thead>
          <tr>
            <th scope="col">Semana</th>
            <th scope="col">Entradas</th>
            <th scope="col">Saídas</th>
          </tr>
        </thead>
        <tbody>
          {semanas.map((s) => (
            <tr key={s.rotulo}>
              <th scope="row">{s.rotulo}</th>
              <td>{formatar(deCentavos(s.entradas))}</td>
              <td>{formatar(deCentavos(s.saidas))}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
