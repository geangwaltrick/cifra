import { useEffect, useState } from 'react';
import * as api from '../api/cifra';
import { IconeOlho, IconeOlhoFechado } from '../Icones';
import { textoDoErro } from '../mensagens';

export function CartaoVirtual() {
  const [cartao, setCartao] = useState<api.Cartao | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);

  useEffect(() => {
    api.meuCartao().then(setCartao).catch((falha) => setErro(textoDoErro(falha)));
  }, []);

  async function alternarVisibilidade() {
    setOcupado(true);
    try {
      // Ao esconder, busca de novo mascarado em vez de so apagar da tela: o
      // numero real deixa de existir no estado do navegador.
      setCartao(await api.meuCartao(!cartao?.revelado));
    } catch (falha) {
      setErro(textoDoErro(falha));
    } finally {
      setOcupado(false);
    }
  }

  async function alternarBloqueio() {
    if (!cartao) return;
    setOcupado(true);
    setErro(null);

    try {
      setCartao(cartao.status === 'ATIVO' ? await api.bloquearCartao() : await api.desbloquearCartao());
    } catch (falha) {
      setErro(textoDoErro(falha));
    } finally {
      setOcupado(false);
    }
  }

  if (erro && !cartao) {
    return (
      <p className="cartao" role="alert">
        {erro}
      </p>
    );
  }
  if (!cartao) return <p className="cartao rotulo">Carregando…</p>;

  const bloqueado = cartao.status !== 'ATIVO';

  return (
    <div className="painel">
      <div className={bloqueado ? 'plastico bloqueado' : 'plastico'}>
        <div className="plastico-topo">
          <span className="plastico-marca">{cartao.bandeira}</span>
          <span className="plastico-tipo">Virtual · Débito</span>
        </div>

        <span className="plastico-numero">{cartao.numero}</span>

        <div className="plastico-rodape">
          <div>
            <span className="plastico-etiqueta">Titular</span>
            <span className="plastico-dado">{cartao.titular}</span>
          </div>
          <div>
            <span className="plastico-etiqueta">Validade</span>
            <span className="plastico-dado">{cartao.validade}</span>
          </div>
          <div>
            <span className="plastico-etiqueta">CVV</span>
            <span className="plastico-dado">{cartao.cvv ?? '•••'}</span>
          </div>
        </div>

        {bloqueado && <span className="plastico-selo">Bloqueado</span>}
      </div>

      <section className="cartao">
        <h2>Segurança</h2>

        <div className="acao-linha">
          <div>
            <span className="titulo">Mostrar dados</span>
            <span className="detalhe">Número completo e código de segurança</span>
          </div>
          <button type="button" className="secundario" onClick={alternarVisibilidade} disabled={ocupado}>
            {cartao.revelado ? <IconeOlhoFechado /> : <IconeOlho />}
            {cartao.revelado ? 'Ocultar' : 'Mostrar'}
          </button>
        </div>

        <div className="acao-linha">
          <div>
            <span className="titulo">{bloqueado ? 'Cartão bloqueado' : 'Bloquear cartão'}</span>
            <span className="detalhe">
              {bloqueado ? 'Desbloqueie para voltar a usar' : 'Você pode desbloquear quando quiser'}
            </span>
          </div>
          <button type="button" className="secundario" onClick={alternarBloqueio} disabled={ocupado}>
            {bloqueado ? 'Desbloquear' : 'Bloquear'}
          </button>
        </div>

        {erro && <p role="alert">{erro}</p>}

        <p className="detalhe">
          Cartão de demonstração. O número é válido pelo algoritmo de Luhn, o mesmo das bandeiras, mas não
          funciona fora deste sistema e não há dinheiro real por trás.
        </p>
      </section>
    </div>
  );
}
