import { useEffect, useState } from 'react';
import { aguardarServidor, type EstadoDoServidor } from './api/despertar';
import { encerrar, observar, sessaoAtual } from './api/sessao';
import { IconeAjustes, IconeConta, IconePix, IconeSair } from './Icones';
import { Acesso } from './telas/Acesso';
import { Ajustes } from './telas/Ajustes';
import { Painel } from './telas/Painel';
import { Pix } from './telas/Pix';
import './App.css';

type Aba = 'conta' | 'pix' | 'ajustes';

const SECOES = [
  { id: 'conta' as const, nome: 'Conta', Icone: IconeConta },
  { id: 'pix' as const, nome: 'PIX', Icone: IconePix },
  { id: 'ajustes' as const, nome: 'Ajustes', Icone: IconeAjustes },
];

export default function App() {
  const [autenticado, setAutenticado] = useState(sessaoAtual() !== null);
  const [aba, setAba] = useState<Aba>('conta');
  const [versao, setVersao] = useState(0);
  const [servidor, setServidor] = useState<EstadoDoServidor>('verificando');

  // A sessao tambem cai de fora: o cliente encerra sozinho quando o refresh
  // falha. Sem observar isso, a tela seguiria mostrando dados de uma sessao
  // que ja nao existe.
  useEffect(() => observar((sessao) => setAutenticado(sessao !== null)), []);
  useEffect(() => void aguardarServidor(setServidor), []);

  if (servidor === 'verificando' || servidor === 'acordando') {
    return (
      <div className="centralizado">
        <div className="cartao">
          <h1>Cifra</h1>
          <p className="rotulo">Acordando o servidor…</p>
          <p>
            Esta demonstração roda em hospedagem gratuita, que suspende o serviço quando fica parado. A
            primeira visita leva até um minuto. As próximas são imediatas.
          </p>
        </div>
      </div>
    );
  }

  if (servidor === 'indisponivel') {
    return (
      <div className="centralizado">
        <div className="cartao">
          <h1>Cifra</h1>
          <p role="alert">O servidor não respondeu. Tente recarregar a página em alguns minutos.</p>
        </div>
      </div>
    );
  }

  if (!autenticado) {
    return (
      <div className="centralizado">
        <Acesso aoEntrar={() => setAutenticado(true)} />
      </div>
    );
  }

  return (
    <div className="aplicacao">
      {/* Barra lateral no desktop, barra inferior no celular -- a mesma marcacao
          nos dois, so muda a direcao e a ancoragem no CSS. */}
      <nav className="navegacao" aria-label="Seções">
        <span className="marca">Cifra</span>

        <ul>
          {SECOES.map(({ id, nome, Icone }) => (
            <li key={id}>
              <button
                type="button"
                className={aba === id ? 'item ativo' : 'item'}
                aria-current={aba === id ? 'page' : undefined}
                onClick={() => setAba(id)}
              >
                <Icone />
                <span>{nome}</span>
              </button>
            </li>
          ))}
        </ul>

        <button type="button" className="item sair" onClick={encerrar}>
          <IconeSair />
          <span>Sair</span>
        </button>
      </nav>

      <main className="conteudo">
        {aba === 'conta' && <Painel recarregar={versao} irParaPix={() => setAba('pix')} />}
        {aba === 'pix' && <Pix aoMovimentar={() => setVersao(versao + 1)} />}
        {aba === 'ajustes' && <Ajustes />}
      </main>
    </div>
  );
}
