import { requisitar } from './cliente';
import type { Dinheiro } from './dinheiro';
import { guardar } from './sessao';

export type Conta = {
  id: number;
  agencia: string;
  numero: string;
  identificacao: string;
  tipo: string;
  status: string;
};

export type Saldo = {
  contaId: number;
  identificacao: string;
  saldo: Dinheiro;
  saldoConferidoNoRazao: Dinheiro;
  atualizadoEm: string;
};

export type LinhaDoExtrato = {
  id: number;
  data: string;
  transacaoId: number;
  tipo: string;
  status: string;
  descricao: string | null;
  valor: Dinheiro;
  sentido: 'DEBITO' | 'CREDITO';
  saldoApos: Dinheiro;
  contraparte: string | null;
};

export type Extrato = {
  conta: string;
  linhas: LinhaDoExtrato[];
  pagina: number;
  tamanho: number;
  total: number;
  totalDePaginas: number;
};

export type Transacao = {
  id: number;
  tipo: string;
  status: string;
  valor: Dinheiro;
  descricao: string | null;
  somaDosLancamentos: Dinheiro;
};

export async function entrar(email: string, senha: string): Promise<void> {
  const tokens = await requisitar<{ accessToken: string; refreshToken: string }>('/api/v1/auth/login', {
    metodo: 'POST',
    corpo: { email, senha },
    autenticada: false,
  });

  guardar({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken });
}

export function cadastrar(dados: { nome: string; cpf: string; email: string; senha: string }) {
  return requisitar<{ usuarioId: number; conta: Conta }>('/api/v1/auth/registro', {
    metodo: 'POST',
    corpo: dados,
    autenticada: false,
  });
}

export const minhaConta = () => requisitar<Conta>('/api/v1/contas/me');

export const meuSaldo = () => requisitar<Saldo>('/api/v1/contas/me/saldo');

export function meuExtrato(pagina = 0, tipo?: string) {
  const filtro = tipo ? `&tipo=${encodeURIComponent(tipo)}` : '';
  return requisitar<Extrato>(`/api/v1/contas/me/extrato?pagina=${pagina}&tamanho=25${filtro}`);
}

/**
 * A chave e gerada uma vez por intencao de pagamento, nao por requisicao.
 * Quem chamar de novo com a mesma chave -- retry manual, clique duplo, rede
 * caindo -- recebe a mesma transacao de volta, sem cobrar duas vezes.
 */
export function depositar(valor: Dinheiro, descricao: string, chave: string) {
  return requisitar<Transacao>('/api/v1/depositos', {
    metodo: 'POST',
    corpo: { valor, descricao },
    idempotencyKey: chave,
  });
}

export function sacar(valor: Dinheiro, descricao: string, chave: string, senha?: string) {
  return requisitar<Transacao>('/api/v1/saques', {
    metodo: 'POST',
    corpo: { valor, descricao },
    idempotencyKey: chave,
    senhaTransacional: senha,
  });
}

export function pagarPix(chavePix: string, valor: Dinheiro, descricao: string, chave: string, senha?: string) {
  return requisitar<Transacao>('/api/v1/pix/transferencias', {
    metodo: 'POST',
    corpo: { chave: chavePix, valor, descricao },
    idempotencyKey: chave,
    senhaTransacional: senha,
  });
}

export type ChavePix = {
  id: number;
  tipo: 'CPF' | 'EMAIL' | 'TELEFONE' | 'ALEATORIA';
  valor: string;
  criadoEm: string;
};

export type Limite = {
  limiteDiario: Dinheiro;
  gastoHoje: Dinheiro;
  disponivelHoje: Dinheiro;
};

export const listarChaves = () => requisitar<ChavePix[]>('/api/v1/pix/chaves');

export function registrarChave(tipo: ChavePix['tipo'], valor: string) {
  return requisitar<ChavePix>('/api/v1/pix/chaves', { metodo: 'POST', corpo: { tipo, valor } });
}

export const removerChave = (id: number) =>
  requisitar<void>(`/api/v1/pix/chaves/${id}`, { metodo: 'DELETE' });

export const meuLimite = () => requisitar<Limite>('/api/v1/contas/me/limite');

export function ajustarLimite(limiteDiario: Dinheiro, senha?: string) {
  return requisitar<Limite>('/api/v1/contas/me/limite', {
    metodo: 'PUT',
    corpo: { limiteDiario },
    senhaTransacional: senha,
  });
}

export function definirSenhaTransacional(senhaDeAcesso: string, senhaTransacional: string) {
  return requisitar<{ mensagem: string }>('/api/v1/seguranca/senha-transacional', {
    metodo: 'POST',
    corpo: { senhaDeAcesso, senhaTransacional },
  });
}

export type Cartao = {
  id: number;
  numero: string;
  cvv: string | null;
  titular: string;
  validade: string;
  status: 'ATIVO' | 'BLOQUEADO' | 'CANCELADO';
  bandeira: string;
  utilizavel: boolean;
  revelado: boolean;
};

/**
 * O numero completo e o CVV so vem quando `revelar` e verdadeiro.
 *
 * Duas chamadas separadas de proposito: a tela abre com o cartao mascarado e
 * o dado sensivel so entra na memoria do navegador quando alguem pede para
 * ver, num gesto deliberado.
 */
export const meuCartao = (revelar = false) =>
  requisitar<Cartao>(`/api/v1/cartoes/me?revelar=${revelar}`);

export const bloquearCartao = () =>
  requisitar<Cartao>('/api/v1/cartoes/me/bloqueio', { metodo: 'POST' });

export const desbloquearCartao = () =>
  requisitar<Cartao>('/api/v1/cartoes/me/desbloqueio', { metodo: 'POST' });

export const novaChaveDeIdempotencia = () => crypto.randomUUID();
