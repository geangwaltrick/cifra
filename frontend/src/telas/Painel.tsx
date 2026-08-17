import { useCallback, useEffect, useState } from 'react';
import * as api from '../api/cifra';
import { deCentavos, doCampo, ehPositivo, formatar, paraCentavos } from '../api/dinheiro';
import { paraOndeFoi, porSemana, resumoDoMes, rotuloDoTipo, type Destino, type Semana } from '../api/resumo';
import { primeiroNome } from '../api/sessao';
import { FluxoSemanal } from '../graficos/FluxoSemanal';
import { IconeEntrada, IconeOlho, IconeOlhoFechado, IconePix, IconeSaida } from '../Icones';
import { textoDoErro } from '../mensagens';

/** Agrupa os lancamentos por dia, do jeito que extrato de banco se le. */
function porDia(linhas: api.LinhaDoExtrato[]): [string, api.LinhaDoExtrato[]][] {
  const grupos = new Map<string, api.LinhaDoExtrato[]>();

  for (const linha of linhas) {
    const dia = new Date(linha.data).toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: 'long',
    });
    grupos.set(dia, [...(grupos.get(dia) ?? []), linha]);
  }

  return [...grupos.entries()];
}

function hora(data: string): string {
  return new Date(data).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

function saudacao(agora = new Date()): string {
  const h = agora.getHours();

  return h < 12 ? 'Bom dia' : h < 18 ? 'Boa tarde' : 'Boa noite';
}

const TRINTA_DIAS = 30 * 86_400_000;

export function Painel({ recarregar, irParaPix }: { recarregar: number; irParaPix: () => void }) {
  const [saldo, setSaldo] = useState<api.Saldo | null>(null);
  const [extrato, setExtrato] = useState<api.Extrato | null>(null);
  const [limite, setLimite] = useState<api.Limite | null>(null);
  const [semanas, setSemanas] = useState<Semana[]>([]);
  const [destinos, setDestinos] = useState<Destino[]>([]);
  const [mes, setMes] = useState(resumoDoMes([]));
  const [pagina, setPagina] = useState(0);
  const [erro, setErro] = useState<string | null>(null);
  const [valor, setValor] = useState('');
  const [depositando, setDepositando] = useState(false);
  const [oculto, setOculto] = useState(false);

  const carregar = useCallback(async () => {
    try {
      // O historico completo alimenta grafico, resumo do mes e destinos; a
      // pagina do extrato e uma consulta a parte porque ela navega, e as tres
      // leituras derivadas nao devem mudar quando alguem vira a pagina.
      // Doze linhas por pagina, e nao vinte e cinco: no painel o extrato divide
      // a tela com o resto: pagina longa demais empurra tudo para fora da vista
      // e o painel volta a ser uma lista com enfeite em cima.
      const [s, e, h] = await Promise.all([
        api.meuSaldo(),
        api.meuExtrato(pagina, undefined, 12),
        api.meuHistorico(),
      ]);

      setSaldo(s);
      setExtrato(e);
      setSemanas(porSemana(h.linhas));
      setDestinos(paraOndeFoi(h.linhas, new Date(Date.now() - TRINTA_DIAS)));
      setMes(resumoDoMes(h.linhas));
      setErro(null);
    } catch (falha) {
      setErro(textoDoErro(falha));
    }
  }, [pagina]);

  // O limite fica fora do carregamento principal de proposito: e o unico dado
  // da tela que nao vem do razao, e uma falha nele nao pode esconder o saldo.
  useEffect(() => {
    api.meuLimite().then(setLimite).catch(() => setLimite(null));
  }, [recarregar]);

  useEffect(() => {
    void carregar();
  }, [carregar, recarregar]);

  async function depositar(evento: React.FormEvent) {
    evento.preventDefault();
    const montante = doCampo(valor);
    if (!ehPositivo(montante)) return;

    try {
      await api.depositar(montante, 'Depósito pelo app', api.novaChaveDeIdempotencia());
      setValor('');
      setDepositando(false);
      await carregar();
    } catch (falha) {
      setErro(textoDoErro(falha));
    }
  }

  if (erro && !saldo) {
    return (
      <p className="cartao" role="alert">
        {erro}
      </p>
    );
  }
  if (!saldo || !extrato) return <p className="cartao rotulo">Carregando…</p>;

  const nome = primeiroNome();
  const usado = limite ? paraCentavos(limite.gastoHoje) / Math.max(paraCentavos(limite.limiteDiario), 1) : 0;
  const sobra = paraCentavos(mes.resultado);

  return (
    <div className="painel largo">
      <header className="cabecalho">
        <h1>
          {saudacao()}
          {nome ? `, ${nome}` : ''}
        </h1>
        <span className="rotulo">
          {new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' })}
        </span>
      </header>

      <section className="cartao destaque larga">
        <span className="rotulo">Conta corrente · {saldo.identificacao}</span>

        <div className="saldo-linha">
          <strong className="saldo">{oculto ? '••••••' : formatar(saldo.saldo)}</strong>
          <button
            type="button"
            className="olho"
            onClick={() => setOculto(!oculto)}
            aria-label={oculto ? 'Mostrar saldo' : 'Ocultar saldo'}
          >
            {oculto ? <IconeOlho /> : <IconeOlhoFechado />}
          </button>
        </div>

        {/* O saldo do razao ao lado do projetado nao e detalhe de implementacao
            exposto por vaidade: e a promessa do produto conferida na tela. */}
        <span className="conferencia">
          Conferido no razão: {oculto ? '••••••' : formatar(saldo.saldoConferidoNoRazao)}
        </span>

        <div className="acoes">
          <button type="button" onClick={() => setDepositando(!depositando)}>
            <IconeEntrada /> Depositar
          </button>
          <button type="button" onClick={irParaPix}>
            <IconePix tamanho={16} /> PIX
          </button>
        </div>

        {depositando && (
          <form className="linha" onSubmit={depositar}>
            <input
              value={valor}
              onChange={(e) => setValor(e.target.value)}
              placeholder="0,00"
              inputMode="decimal"
              aria-label="Valor do depósito"
              autoFocus
            />
            <button type="submit">Confirmar</button>
          </form>
        )}
      </section>

      <section className="cartao">
        <div className="entre-linhas">
          <h2>Limite diário</h2>
          <span className="rotulo">hoje</span>
        </div>

        {limite ? (
          <>
            <div className="medidor-topo">
              <strong className="valor-forte">{formatar(limite.disponivelHoje)}</strong>
              <span className="rotulo">disponível</span>
            </div>
            <div
              className="medidor"
              role="meter"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={Math.round(usado * 100)}
              aria-label="Limite diário consumido"
            >
              <span style={{ width: `${Math.min(usado * 100, 100)}%` }} />
            </div>
            <span className="detalhe">
              {formatar(limite.gastoHoje)} de {formatar(limite.limiteDiario)} usados hoje
            </span>
          </>
        ) : (
          <p>Limite indisponível no momento.</p>
        )}
      </section>

      {erro && <p role="alert">{erro}</p>}

      {/* Tres numeros que respondem "como foi o mes" antes de qualquer rolagem.
          O mes aparece uma vez, no titulo da faixa: repetido em cada rotulo ele
          quebrava em duas linhas na largura de um celular. */}
      <div className="tiles">
        <span className="rotulo faixa-titulo">em {mes.mes}</span>

        <article className="tile">
          <span className="rotulo">Entradas</span>
          <strong className="credito">{formatar(mes.entradas)}</strong>
        </article>
        <article className="tile">
          <span className="rotulo">Saídas</span>
          <strong>{formatar(mes.saidas)}</strong>
        </article>
        <article className="tile">
          <span className="rotulo">Resultado</span>
          <strong className={sobra >= 0 ? 'credito' : 'negativo'}>
            {sobra > 0 ? '+' : ''}
            {formatar(mes.resultado)}
          </strong>
          <span className="detalhe">{mes.movimentos} movimentações</span>
        </article>
      </div>

      <section className="cartao larga">
        <div className="entre-linhas">
          <h2>Fluxo das últimas 8 semanas</h2>
        </div>
        <FluxoSemanal semanas={semanas} />
      </section>

      <section className="cartao">
        <div className="entre-linhas">
          <h2>Para onde foi</h2>
          <span className="rotulo">30 dias</span>
        </div>

        {destinos.length === 0 && <p>Nenhuma saída nos últimos 30 dias.</p>}

        <ul className="destinos">
          {destinos.map((destino) => (
            <li key={destino.nome}>
              <span className="destino-nome">{destino.nome}</span>
              <span className="destino-valor">{formatar(deCentavos(destino.centavos))}</span>
              <span className="destino-barra" aria-hidden="true">
                <i style={{ width: `${Math.max(destino.fatia * 100, 4)}%` }} />
              </span>
            </li>
          ))}
        </ul>
      </section>

      <section className="cartao total">
        <div className="entre-linhas">
          <h2>Extrato</h2>
          <span className="rotulo">{extrato.total} lançamentos</span>
        </div>

        {extrato.linhas.length === 0 && <p>Nenhuma movimentação ainda.</p>}

        {porDia(extrato.linhas).map(([dia, movimentos]) => (
          <div className="grupo" key={dia}>
            <span className="dia">{dia}</span>

            {movimentos.map((linha) => (
              <div className="movimento" key={linha.id}>
                <span
                  className={`selo ${linha.sentido === 'DEBITO' ? 'saida' : 'entrada'}`}
                  aria-hidden="true"
                >
                  {linha.sentido === 'DEBITO' ? <IconeSaida /> : <IconeEntrada />}
                </span>

                <div className="movimento-texto">
                  <span className="titulo">{linha.descricao ?? rotuloDoTipo(linha.tipo)}</span>
                  <span className="detalhe">
                    {linha.contraparte ?? (linha.sentido === 'DEBITO' ? 'Saída' : 'Entrada')} ·{' '}
                    {hora(linha.data)}
                  </span>
                </div>

                <div className="movimento-valor">
                  <span className={linha.sentido === 'DEBITO' ? 'debito' : 'credito'}>
                    {formatar(linha.valor)}
                  </span>
                  <span className="detalhe">{formatar(linha.saldoApos)}</span>
                </div>
              </div>
            ))}
          </div>
        ))}

        {extrato.totalDePaginas > 1 && (
          <div className="paginacao">
            <button type="button" className="link" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>
              ← Anterior
            </button>
            <span className="rotulo">
              {pagina + 1} de {extrato.totalDePaginas}
            </span>
            <button
              type="button"
              className="link"
              disabled={pagina + 1 >= extrato.totalDePaginas}
              onClick={() => setPagina(pagina + 1)}
            >
              Próxima →
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
