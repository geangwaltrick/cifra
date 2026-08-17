import { useEffect, useRef, useState } from 'react';
import * as api from '../api/cifra';
import { doCampo, ehPositivo, formatar } from '../api/dinheiro';
import { exigeSenhaTransacional, textoDoErro } from '../mensagens';

const TIPOS: { valor: api.ChavePix['tipo']; nome: string; ajuda: string }[] = [
  { valor: 'ALEATORIA', nome: 'Aleatória', ajuda: 'Gerada pelo sistema, sem revelar seus dados' },
  { valor: 'CPF', nome: 'CPF', ajuda: 'Precisa ser o CPF do titular da conta' },
  { valor: 'EMAIL', nome: 'E-mail', ajuda: 'Precisa ser o e-mail cadastrado' },
  { valor: 'TELEFONE', nome: 'Telefone', ajuda: 'Com DDD, somente números' },
];

export function Pix({ aoMovimentar }: { aoMovimentar: () => void }) {
  const [aba, setAba] = useState<'pagar' | 'chaves'>('pagar');
  const [chaves, setChaves] = useState<api.ChavePix[]>([]);

  const [novoTipo, setNovoTipo] = useState<api.ChavePix['tipo']>('ALEATORIA');
  const [novoValor, setNovoValor] = useState('');

  const [destino, setDestino] = useState('');
  const [valor, setValor] = useState('');
  const [senha, setSenha] = useState('');
  const [pedeSenha, setPedeSenha] = useState(false);

  const [erro, setErro] = useState<string | null>(null);
  const [comprovante, setComprovante] = useState<api.Transacao | null>(null);
  const [ocupado, setOcupado] = useState(false);

  /**
   * A chave nasce com a intencao de pagar, nao com a requisicao.
   *
   * Se fosse gerada a cada envio, um retry depois de timeout viraria um
   * segundo pagamento e a idempotencia do servidor nao teria o que proteger.
   * So e trocada depois de um pagamento concluido.
   */
  const chaveDoPagamento = useRef(api.novaChaveDeIdempotencia());

  useEffect(() => {
    void api.listarChaves().then(setChaves).catch(() => setChaves([]));
  }, []);

  async function registrar(evento: React.FormEvent) {
    evento.preventDefault();
    setErro(null);
    try {
      setChaves([...chaves, await api.registrarChave(novoTipo, novoValor)]);
      setNovoValor('');
    } catch (falha) {
      setErro(textoDoErro(falha));
    }
  }

  async function remover(id: number) {
    await api.removerChave(id);
    setChaves(chaves.filter((chave) => chave.id !== id));
  }

  async function pagar(evento: React.FormEvent) {
    evento.preventDefault();
    const montante = doCampo(valor);

    if (!ehPositivo(montante)) {
      setErro('Informe um valor maior que zero.');
      return;
    }

    setOcupado(true);
    setErro(null);

    try {
      const transacao = await api.pagarPix(
        destino,
        montante,
        'PIX enviado pelo app',
        chaveDoPagamento.current,
        senha || undefined,
      );

      setComprovante(transacao);
      setDestino('');
      setValor('');
      setSenha('');
      setPedeSenha(false);
      chaveDoPagamento.current = api.novaChaveDeIdempotencia();
      aoMovimentar();
    } catch (falha) {
      // A chave nao muda aqui: a proxima tentativa e a MESMA intencao.
      setPedeSenha(exigeSenhaTransacional(falha));
      setErro(textoDoErro(falha));
    } finally {
      setOcupado(false);
    }
  }

  return (
    <div className="painel">
      <div className="segmentos" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={aba === 'pagar'}
          className={aba === 'pagar' ? 'segmento ativo' : 'segmento'}
          onClick={() => setAba('pagar')}
        >
          Pagar
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={aba === 'chaves'}
          className={aba === 'chaves' ? 'segmento ativo' : 'segmento'}
          onClick={() => setAba('chaves')}
        >
          Minhas chaves
          {chaves.length > 0 && <span className="contador">{chaves.length}</span>}
        </button>
      </div>

      {aba === 'pagar' && (
        <>
          {comprovante ? (
            <section className="cartao">
              <span className="rotulo">Comprovante {comprovante.id}</span>
              <strong className="saldo">{formatar(comprovante.valor)}</strong>

              <div className="acao-linha">
                <div>
                  <span className="titulo">Situação</span>
                  <span className="detalhe">{comprovante.status}</span>
                </div>
                <span className="pastilha ok">Pago</span>
              </div>

              <div className="acao-linha">
                <div>
                  <span className="titulo">Lançamentos</span>
                  <span className="detalhe">Somam {comprovante.somaDosLancamentos} — o razão fecha</span>
                </div>
              </div>

              <button type="button" className="secundario" onClick={() => setComprovante(null)}>
                Fazer outro PIX
              </button>
            </section>
          ) : (
            <section className="cartao">
              <h2>Pagar com PIX</h2>
              <form onSubmit={pagar}>
                <label>
                  Chave de destino
                  <input
                    value={destino}
                    onChange={(e) => setDestino(e.target.value)}
                    placeholder="CPF, e-mail, telefone ou chave aleatória"
                    required
                  />
                </label>
                <label>
                  Valor
                  <input
                    value={valor}
                    onChange={(e) => setValor(e.target.value)}
                    placeholder="0,00"
                    inputMode="decimal"
                    required
                  />
                </label>
                {pedeSenha && (
                  <label>
                    Senha de movimentação
                    <input type="password" value={senha} onChange={(e) => setSenha(e.target.value)} />
                  </label>
                )}
                {erro && <p role="alert">{erro}</p>}
                <button type="submit" disabled={ocupado}>
                  {ocupado ? 'Enviando…' : 'Pagar'}
                </button>
              </form>
            </section>
          )}
        </>
      )}

      {aba === 'chaves' && (
        <>
          <section className="cartao">
            <h2>Chaves registradas</h2>

            {chaves.length === 0 && (
              <p className="detalhe">
                Nenhuma chave ainda. Registre uma abaixo para receber PIX sem passar agência e conta.
              </p>
            )}

            {chaves.map((chave) => (
              <div className="acao-linha" key={chave.id}>
                <div>
                  <span className="titulo">{TIPOS.find((t) => t.valor === chave.tipo)?.nome ?? chave.tipo}</span>
                  <span className="detalhe chave-valor">{chave.valor}</span>
                </div>
                <button type="button" className="secundario" onClick={() => void remover(chave.id)}>
                  Remover
                </button>
              </div>
            ))}
          </section>

          <section className="cartao">
            <h2>Registrar chave</h2>
            <form onSubmit={registrar}>
              <label>
                Tipo
                <select value={novoTipo} onChange={(e) => setNovoTipo(e.target.value as api.ChavePix['tipo'])}>
                  {TIPOS.map((tipo) => (
                    <option key={tipo.valor} value={tipo.valor}>
                      {tipo.nome}
                    </option>
                  ))}
                </select>
              </label>

              <p className="detalhe">{TIPOS.find((t) => t.valor === novoTipo)?.ajuda}</p>

              {novoTipo !== 'ALEATORIA' && (
                <label>
                  Valor da chave
                  <input value={novoValor} onChange={(e) => setNovoValor(e.target.value)} required />
                </label>
              )}

              {erro && <p role="alert">{erro}</p>}
              <button type="submit">Registrar</button>
            </form>
          </section>
        </>
      )}
    </div>
  );
}
