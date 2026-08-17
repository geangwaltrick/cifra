import { useCallback, useEffect, useState } from 'react';
import * as api from '../api/cifra';
import { doCampo, ehPositivo, formatar } from '../api/dinheiro';
import { textoDoErro } from '../mensagens';

export function Painel({ recarregar }: { recarregar: number }) {
  const [saldo, setSaldo] = useState<api.Saldo | null>(null);
  const [extrato, setExtrato] = useState<api.Extrato | null>(null);
  const [pagina, setPagina] = useState(0);
  const [erro, setErro] = useState<string | null>(null);
  const [valor, setValor] = useState('');

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
      await carregar();
    } catch (falha) {
      setErro(textoDoErro(falha));
    }
  }

  if (erro && !saldo) return <p className="cartao" role="alert">{erro}</p>;
  if (!saldo || !extrato) return <p className="cartao rotulo">Carregando…</p>;

  return (
    <div className="painel">
      <section className="cartao destaque">
        <span className="rotulo">Conta corrente · {saldo.identificacao}</span>
        <strong className="saldo">{formatar(saldo.saldo)}</strong>
        <form className="linha" onSubmit={depositar}>
          <input value={valor} onChange={(e) => setValor(e.target.value)} placeholder="0,00" inputMode="decimal" />
          <button type="submit">Depositar</button>
        </form>
        {erro && <p role="alert">{erro}</p>}
      </section>

      <section className="cartao">
        <h2>Extrato</h2>
        <table>
          <tbody>
            {extrato.linhas.map((linha) => (
              <tr key={linha.id}>
                <td className="rotulo">{new Date(linha.data).toLocaleDateString('pt-BR')}</td>
                <td>
                  {linha.descricao ?? linha.tipo}
                  {linha.contraparte && <div className="rotulo">{linha.contraparte}</div>}
                </td>
                <td className={linha.sentido === 'DEBITO' ? 'debito' : 'credito'}>{formatar(linha.valor)}</td>
                <td className="rotulo">{formatar(linha.saldoApos)}</td>
              </tr>
            ))}
          </tbody>
        </table>

        {extrato.linhas.length === 0 && <p className="rotulo">Nenhuma movimentação ainda.</p>}

        {extrato.totalDePaginas > 1 && (
          <div className="linha">
            <button type="button" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>
              Anterior
            </button>
            <span className="rotulo">
              {pagina + 1} de {extrato.totalDePaginas}
            </span>
            <button
              type="button"
              disabled={pagina + 1 >= extrato.totalDePaginas}
              onClick={() => setPagina(pagina + 1)}
            >
              Próxima
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
