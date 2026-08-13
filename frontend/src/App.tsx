import { useEffect, useState } from 'react';
import { encerrar, observar, sessaoAtual } from './api/sessao';
import { Acesso } from './telas/Acesso';
import { Ajustes } from './telas/Ajustes';
import { Painel } from './telas/Painel';
import { Pix } from './telas/Pix';
import './App.css';

type Aba = 'conta' | 'pix' | 'ajustes';

export default function App() {
  const [autenticado, setAutenticado] = useState(sessaoAtual() !== null);
  const [aba, setAba] = useState<Aba>('conta');

  /** Muda quando algo movimenta dinheiro, forçando o painel a reler o saldo. */
  const [versao, setVersao] = useState(0);

  // A sessao tambem cai de fora: o cliente encerra sozinho quando o refresh
  // falha. Sem observar isso, a tela seguiria mostrando dados de uma sessao
  // que ja nao existe.
  useEffect(() => observar((sessao) => setAutenticado(sessao !== null)), []);

  if (!autenticado) {
    return <Acesso aoEntrar={() => setAutenticado(true)} />;
  }

  return (
    <>
      <nav className="abas">
        {(['conta', 'pix', 'ajustes'] as const).map((nome) => (
          <button
            key={nome}
            type="button"
            className={aba === nome ? 'aba ativa' : 'aba'}
            onClick={() => setAba(nome)}
          >
            {nome === 'conta' ? 'Conta' : nome === 'pix' ? 'PIX' : 'Ajustes'}
          </button>
        ))}
        <button type="button" className="link" onClick={encerrar}>
          Sair
        </button>
      </nav>

      {aba === 'conta' && <Painel recarregar={versao} />}
      {aba === 'pix' && <Pix aoMovimentar={() => setVersao(versao + 1)} />}
      {aba === 'ajustes' && <Ajustes />}
    </>
  );
}
