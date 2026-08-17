import { useEffect, useState } from 'react';
import * as api from '../api/cifra';
import { doCampo, formatar, paraCentavos } from '../api/dinheiro';
import { exigeSenhaTransacional, textoDoErro } from '../mensagens';

export function Ajustes() {
  const [limite, setLimite] = useState<api.Limite | null>(null);
  const [editandoLimite, setEditandoLimite] = useState(false);
  const [editandoSenha, setEditandoSenha] = useState(false);

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
      setEditandoLimite(false);
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
      setEditandoSenha(false);
      setAviso('Senha de movimentação definida. Ela passa a ser exigida para mover dinheiro.');
    } catch (falha) {
      setErro(textoDoErro(falha));
    }
  }

  // Quanto do teto ja foi consumido hoje, para a barra e para o texto.
  const consumo =
    limite && paraCentavos(limite.limiteDiario) > 0
      ? Math.min(100, (paraCentavos(limite.gastoHoje) / paraCentavos(limite.limiteDiario)) * 100)
      : 0;

  return (
    <div className="painel">
      <section className="cartao">
        <h2>Limite diário</h2>

        {limite && (
          <>
            <div className="medidor-topo">
              <strong className="valor-forte">{formatar(limite.disponivelHoje)}</strong>
              <span className="detalhe">disponível hoje</span>
            </div>

            {/* Barra antes do numero: o quanto sobrou se le de relance,
                enquanto tres valores em texto exigem comparar na cabeca. */}
            <div
              className="medidor"
              role="meter"
              aria-valuenow={Math.round(consumo)}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label="Consumo do limite diário"
            >
              <span style={{ width: `${consumo}%` }} />
            </div>

            <div className="acao-linha">
              <div>
                <span className="titulo">Teto de {formatar(limite.limiteDiario)}</span>
                <span className="detalhe">Já usou {formatar(limite.gastoHoje)} hoje</span>
              </div>
              <button type="button" className="secundario" onClick={() => setEditandoLimite(!editandoLimite)}>
                {editandoLimite ? 'Cancelar' : 'Alterar'}
              </button>
            </div>
          </>
        )}

        {editandoLimite && (
          <form onSubmit={salvarLimite}>
            <label>
              Novo teto
              <input
                value={novoLimite}
                onChange={(e) => setNovoLimite(e.target.value)}
                placeholder="5.000,00"
                inputMode="decimal"
                required
                autoFocus
              />
            </label>
            {pedeSenha && (
              <label>
                Senha de movimentação
                <input type="password" value={senhaAtual} onChange={(e) => setSenhaAtual(e.target.value)} />
              </label>
            )}
            <button type="submit">Salvar limite</button>
          </form>
        )}
      </section>

      <section className="cartao">
        <h2>Senha de movimentação</h2>

        <div className="acao-linha">
          <div>
            <span className="titulo">Senha separada para mover dinheiro</span>
            <span className="detalhe">Ler saldo e extrato continua livre; pagar passa a exigi-la</span>
          </div>
          <button type="button" className="secundario" onClick={() => setEditandoSenha(!editandoSenha)}>
            {editandoSenha ? 'Cancelar' : 'Definir'}
          </button>
        </div>

        {editandoSenha && (
          <form onSubmit={salvarSenha}>
            <label>
              Sua senha de acesso
              <input
                type="password"
                value={senhaDeAcesso}
                onChange={(e) => setSenhaDeAcesso(e.target.value)}
                required
                autoFocus
              />
            </label>
            <label>
              Nova senha de movimentação
              <input
                type="password"
                value={novaSenha}
                onChange={(e) => setNovaSenha(e.target.value)}
                required
                minLength={6}
              />
            </label>
            <button type="submit">Definir</button>
          </form>
        )}
      </section>

      {erro && <p role="alert">{erro}</p>}
      {aviso && <p className="aviso">{aviso}</p>}
    </div>
  );
}
