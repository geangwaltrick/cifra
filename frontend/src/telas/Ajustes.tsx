import { useEffect, useState } from 'react';
import * as api from '../api/cifra';
import { doCampo, formatar } from '../api/dinheiro';
import { exigeSenhaTransacional, textoDoErro } from '../mensagens';

export function Ajustes() {
  const [limite, setLimite] = useState<api.Limite | null>(null);
  const [novoLimite, setNovoLimite] = useState('');
  const [senhaAtual, setSenhaAtual] = useState('');
  const [senhaDeAcesso, setSenhaDeAcesso] = useState('');
  const [novaSenha, setNovaSenha] = useState('');
  const [pedeSenha, setPedeSenha] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  useEffect(() => {
    void api.meuLimite().then(setLimite).catch(() => undefined);
  }, []);

  async function salvarLimite(evento: React.FormEvent) {
    evento.preventDefault();
    setErro(null);
    setAviso(null);

    try {
      setLimite(await api.ajustarLimite(doCampo(novoLimite), senhaAtual || undefined));
      setNovoLimite('');
      setSenhaAtual('');
      setPedeSenha(false);
      setAviso('Limite atualizado.');
    } catch (falha) {
      setPedeSenha(exigeSenhaTransacional(falha));
      setErro(textoDoErro(falha));
    }
  }

  async function salvarSenha(evento: React.FormEvent) {
    evento.preventDefault();
    setErro(null);
    setAviso(null);

    try {
      await api.definirSenhaTransacional(senhaDeAcesso, novaSenha);
      setSenhaDeAcesso('');
      setNovaSenha('');
      setAviso('Senha de movimentação definida. Ela passa a ser exigida para mover dinheiro.');
    } catch (falha) {
      setErro(textoDoErro(falha));
    }
  }

  return (
    <div className="painel">
      <section className="cartao">
        <h2>Limite diário</h2>
        {limite && (
          <p className="rotulo">
            Teto {formatar(limite.limiteDiario)} · gasto hoje {formatar(limite.gastoHoje)} · disponível{' '}
            {formatar(limite.disponivelHoje)}
          </p>
        )}
        <form onSubmit={salvarLimite}>
          <label>
            Novo teto
            <input value={novoLimite} onChange={(e) => setNovoLimite(e.target.value)} placeholder="5.000,00" inputMode="decimal" required />
          </label>
          {pedeSenha && (
            <label>
              Senha de movimentação
              <input type="password" value={senhaAtual} onChange={(e) => setSenhaAtual(e.target.value)} />
            </label>
          )}
          <button type="submit">Salvar limite</button>
        </form>
      </section>

      <section className="cartao">
        <h2>Senha de movimentação</h2>
        <p className="rotulo">
          Separada da senha de acesso. Ler saldo e extrato continua livre; mover dinheiro passa a exigi-la.
        </p>
        <form onSubmit={salvarSenha}>
          <label>
            Sua senha de acesso
            <input type="password" value={senhaDeAcesso} onChange={(e) => setSenhaDeAcesso(e.target.value)} required />
          </label>
          <label>
            Nova senha de movimentação
            <input type="password" value={novaSenha} onChange={(e) => setNovaSenha(e.target.value)} required minLength={6} />
          </label>
          <button type="submit">Definir</button>
        </form>
      </section>

      {erro && <p className="cartao" role="alert">{erro}</p>}
      {aviso && <p className="cartao aviso">{aviso}</p>}
    </div>
  );
}
