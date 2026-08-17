import { useCallback, useEffect, useState } from 'react';
import * as api from '../api/cifra';
import { doCampo, ehPositivo, formatar } from '../api/dinheiro';
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

export function Painel({ recarregar, irParaPix }: { recarregar: number; irParaPix: () => void }) {
  const [saldo, setSaldo] = useState<api.Saldo | null>(null);
  const [extrato, setExtrato] = useState<api.Extrato | null>(null);
  const [pagina, setPagina] = useState(0);
  const [erro, setErro] = useState<string | null>(null);
  const [valor, setValor] = useState('');
  const [depositando, setDepositando] = useState(false);
  const [oculto, setOculto] = useState(false);

  const carregar = useCallback(async () => {
    try {
      const [s, e] = await Promise.all([api.meuSaldo(), api.meuExtrato(pagina)]);
      setSaldo(s);
      setExtrato(e);
      setErro(null);
    } catch (falha) {
      setErro(textoDoErro(falha));
    }
  }, [pagina]);

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

  return (
    <div className="painel">
      <section className="cartao destaque">
        <span className="rotulo">Conta corrente · {saldo.identificacao}</span>

        <div className="saldo-linha">
          <strong className="saldo">{oculto ? '••••••' : formatar(saldo.saldo)}</strong>
          <button type="button" className="olho" onClick={() => setOculto(!oculto)}>
            {oculto ? 'mostrar' : 'ocultar'}
          </button>
        </div>

        <div className="acoes">
          <button type="button" onClick={() => setDepositando(!depositando)}>
            <span aria-hidden="true">↓</span> Depositar
          </button>
          <button type="button" onClick={irParaPix}>
            <span aria-hidden="true">↗</span> PIX
          </button>
        </div>

        {depositando && (
          <form className="linha" onSubmit={depositar}>
            <input
              value={valor}
              onChange={(e) => setValor(e.target.value)}
              placeholder="0,00"
              inputMode="decimal"
              autoFocus
            />
            <button type="submit">Confirmar</button>
          </form>
        )}
      </section>

      {erro && (
        <p role="alert">{erro}</p>
      )}

      <section className="cartao">
        <h2>Extrato</h2>

        {extrato.linhas.length === 0 && <p>Nenhuma movimentação ainda.</p>}

        {porDia(extrato.linhas).map(([dia, movimentos]) => (
          <div className="grupo" key={dia}>
            <span className="dia">{dia}</span>

            {movimentos.map((linha) => (
              <div className="movimento" key={linha.id}>
                <span className={`selo ${linha.sentido === 'DEBITO' ? 'saida' : 'entrada'}`} aria-hidden="true">
                  {linha.sentido === 'DEBITO' ? '↑' : '↓'}
                </span>

                <div className="movimento-texto">
                  <span className="titulo">{linha.descricao ?? linha.tipo}</span>
                  <span className="detalhe">
                    {linha.contraparte ?? (linha.sentido === 'DEBITO' ? 'Saída' : 'Entrada')} · {hora(linha.data)}
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
