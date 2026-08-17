import { useState } from 'react';
import * as api from '../api/cifra';
import { textoDoErro } from '../mensagens';

type Modo = 'entrar' | 'cadastrar';

/**
 * Conta de demonstracao ja preenchida.
 *
 * Quem abre o link tem poucos segundos de paciencia, e um formulario vazio
 * exige decidir se vale a pena criar conta antes de ver qualquer coisa. Vindo
 * preenchido, o caminho ate o extrato e um clique. Quem quiser conta propria
 * digita por cima.
 */
const DEMO = { email: 'demo@cifra.app', senha: 'demonstracao1' };

export function Acesso({ aoEntrar }: { aoEntrar: () => void }) {
  const [modo, setModo] = useState<Modo>('entrar');
  const [nome, setNome] = useState('');
  const [cpf, setCpf] = useState('');
  const [email, setEmail] = useState(DEMO.email);
  const [senha, setSenha] = useState(DEMO.senha);
  const [erro, setErro] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    setOcupado(true);
    setErro(null);
    setAviso(null);

    try {
      if (modo === 'entrar') {
        await api.entrar(email, senha);
        aoEntrar();
      } else {
        await api.cadastrar({ nome, cpf, email, senha });
        setModo('entrar');
        setAviso('Conta criada. Confirme o e-mail para ativar e depois entre.');
      }
    } catch (falha) {
      setErro(textoDoErro(falha));
    } finally {
      setOcupado(false);
    }
  }

  return (
    <form className="cartao" onSubmit={enviar}>
      <h1>Cifra</h1>
      <p className="rotulo">{modo === 'entrar' ? 'Entrar na conta' : 'Abrir conta'}</p>

      {modo === 'entrar' && (
        <p className="aviso">
          Conta de demonstração já preenchida — é só entrar. Ou abra a sua, se preferir.
        </p>
      )}

      {modo === 'cadastrar' && (
        <>
          <label>
            Nome
            <input value={nome} onChange={(e) => setNome(e.target.value)} required minLength={2} />
          </label>
          <label>
            CPF
            <input
              value={cpf}
              onChange={(e) => setCpf(e.target.value)}
              placeholder="000.000.000-00"
              inputMode="numeric"
              required
            />
          </label>
        </>
      )}

      <label>
        E-mail
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      </label>
      <label>
        Senha
        <input
          type="password"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          required
          minLength={modo === 'cadastrar' ? 8 : undefined}
        />
      </label>

      {erro && <p role="alert">{erro}</p>}
      {aviso && <p className="aviso">{aviso}</p>}

      <button type="submit" disabled={ocupado}>
        {ocupado ? 'Aguarde…' : modo === 'entrar' ? 'Entrar' : 'Criar conta'}
      </button>

      <button
        type="button"
        className="link"
        onClick={() => {
          const proximo = modo === 'entrar' ? 'cadastrar' : 'entrar';
          setModo(proximo);
          // Ao ir para o cadastro, limpa os dados da demo; ao voltar, repoe.
          setEmail(proximo === 'entrar' ? DEMO.email : '');
          setSenha(proximo === 'entrar' ? DEMO.senha : '');
          setErro(null);
          setAviso(null);
        }}
      >
        {modo === 'entrar' ? 'Não tenho conta' : 'Já tenho conta'}
      </button>
    </form>
  );
}
