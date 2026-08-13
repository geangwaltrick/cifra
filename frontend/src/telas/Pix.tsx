import { useEffect, useRef, useState } from 'react';
import * as api from '../api/cifra';
import { doCampo, ehPositivo, formatar } from '../api/dinheiro';
import { exigeSenhaTransacional, textoDoErro } from '../mensagens';

export function Pix({ aoMovimentar }: { aoMovimentar: () => void }) {
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
   * Se ela fosse gerada a cada envio, um retry depois de timeout viraria um
   * segundo pagamento e a idempotencia do servidor nao teria o que proteger.
   * Reusar a mesma chave e o que faz o reenvio devolver a mesma transacao.
   * So zera depois de um pagamento concluido.
   */
  const chaveDoPagamento = useRef(api.novaChaveDeIdempotencia());

  useEffect(() => {
    void api.listarChaves().then(setChaves).catch(() => setChaves([]));
  }, []);

  async function registrar(evento: React.FormEvent) {
    evento.preventDefault();
    setErro(null);
    try {
      const criada = await api.registrarChave(novoTipo, novoValor);
      setChaves([...chaves, criada]);
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
      // Nao troca a chave aqui: a proxima tentativa e a MESMA intencao.
      setPedeSenha(exigeSenhaTransacional(falha));
      setErro(textoDoErro(falha));
    } finally {
      setOcupado(false);
    }
  }

  return (
    <div className="painel">
      <section className="cartao">
        <h2>Pagar com PIX</h2>
        <form onSubmit={pagar}>
          <label>
            Chave de destino
            <input value={destino} onChange={(e) => setDestino(e.target.value)} required />
          </label>
          <label>
            Valor
            <input value={valor} onChange={(e) => setValor(e.target.value)} placeholder="0,00" inputMode="decimal" required />
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

        {comprovante && (
          <div className="comprovante">
            <p className="rotulo">Comprovante {comprovante.id}</p>
            <strong>{formatar(comprovante.valor)}</strong>
            <p className="rotulo">
              {comprovante.status} · lançamentos somam {comprovante.somaDosLancamentos}
            </p>
          </div>
        )}
      </section>

      <section className="cartao">
        <h2>Minhas chaves</h2>
        <ul className="lista">
          {chaves.map((chave) => (
            <li key={chave.id}>
              <span className="rotulo">{chave.tipo}</span>
              <code>{chave.valor}</code>
              <button type="button" className="link" onClick={() => void remover(chave.id)}>
                remover
              </button>
            </li>
          ))}
          {chaves.length === 0 && <li className="rotulo">Nenhuma chave registrada.</li>}
        </ul>

        <form onSubmit={registrar}>
          <label>
            Tipo
            <select value={novoTipo} onChange={(e) => setNovoTipo(e.target.value as api.ChavePix['tipo'])}>
              <option value="ALEATORIA">Aleatória</option>
              <option value="CPF">CPF</option>
              <option value="EMAIL">E-mail</option>
              <option value="TELEFONE">Telefone</option>
            </select>
          </label>
          {novoTipo !== 'ALEATORIA' && (
            <label>
              Valor
              <input value={novoValor} onChange={(e) => setNovoValor(e.target.value)} required />
            </label>
          )}
          <button type="submit">Registrar chave</button>
        </form>
      </section>
    </div>
  );
}
