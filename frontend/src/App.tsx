import { useCallback, useEffect, useState } from 'react';
import * as api from './api/cifra';
import { ProblemaDaApi } from './api/cliente';
import { formatar } from './api/dinheiro';
import { encerrar, observar, sessaoAtual } from './api/sessao';
import './App.css';

function Login({ aoEntrar }: { aoEntrar: () => void }) {
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    setOcupado(true);
    setErro(null);

    try {
      await api.entrar(email, senha);
      aoEntrar();
    } catch (falha) {
      // O texto vem do `type` conhecido, nao do `detail` bruto da API.
      const problema = falha as ProblemaDaApi;
      setErro(
        problema.ehDoTipo('email-nao-verificado')
          ? 'Confirme seu e-mail antes de entrar.'
          : problema.ehDoTipo('excesso-de-tentativas')
            ? 'Tentativas demais. Aguarde alguns minutos.'
            : 'E-mail ou senha incorretos.',
      );
    } finally {
      setOcupado(false);
    }
  }

  return (
    <form className="cartao" onSubmit={enviar}>
      <h1>Cifra</h1>
      <label>
        E-mail
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      </label>
      <label>
        Senha
        <input type="password" value={senha} onChange={(e) => setSenha(e.target.value)} required />
      </label>
      {erro && <p role="alert">{erro}</p>}
      <button type="submit" disabled={ocupado}>
        {ocupado ? 'Entrando…' : 'Entrar'}
      </button>
    </form>
  );
}

function Painel({ aoSair }: { aoSair: () => void }) {
  const [saldo, setSaldo] = useState<api.Saldo | null>(null);
  const [extrato, setExtrato] = useState<api.Extrato | null>(null);

  const carregar = useCallback(async () => {
    const [s, e] = await Promise.all([api.meuSaldo(), api.meuExtrato()]);
    setSaldo(s);
    setExtrato(e);
  }, []);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  if (!saldo || !extrato) return <p className="cartao">Carregando…</p>;

  return (
    <div className="painel">
      <header className="cartao">
        <span className="rotulo">{saldo.identificacao}</span>
        <strong className="saldo">{formatar(saldo.saldo)}</strong>
        <button onClick={aoSair}>Sair</button>
      </header>

      <section className="cartao">
        <h2>Extrato</h2>
        <table>
          <tbody>
            {extrato.linhas.map((linha) => (
              <tr key={linha.id}>
                <td>{new Date(linha.data).toLocaleDateString('pt-BR')}</td>
                <td>{linha.descricao ?? linha.tipo}</td>
                <td className={linha.sentido === 'DEBITO' ? 'debito' : 'credito'}>{formatar(linha.valor)}</td>
                <td className="rotulo">{formatar(linha.saldoApos)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {extrato.linhas.length === 0 && <p className="rotulo">Nenhuma movimentação ainda.</p>}
      </section>
    </div>
  );
}

export default function App() {
  const [autenticado, setAutenticado] = useState(sessaoAtual() !== null);

  // A sessao tambem cai de fora: o cliente encerra sozinho quando o refresh
  // falha. Sem observar isso, a tela ficaria mostrando dados de uma sessao
  // que ja nao existe.
  useEffect(() => observar((sessao) => setAutenticado(sessao !== null)), []);

  return autenticado ? (
    <Painel aoSair={encerrar} />
  ) : (
    <Login aoEntrar={() => setAutenticado(true)} />
  );
}
