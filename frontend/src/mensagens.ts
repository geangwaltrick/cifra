import { ProblemaDaApi } from './api/cliente';

/**
 * Traduz o `type` do problema para o que a pessoa precisa ler.
 *
 * O mapa e por `type` justamente porque ele e estavel: o `detail` que a API
 * manda muda com o tempo e as vezes carrega vocabulario interno. Um `type`
 * desconhecido cai no texto generico em vez de vazar mensagem de servidor.
 */
const TEXTOS: Record<string, string> = {
  'credenciais-invalidas': 'E-mail ou senha incorretos.',
  'email-nao-verificado': 'Confirme seu e-mail antes de entrar.',
  'excesso-de-tentativas': 'Tentativas demais. Aguarde alguns minutos.',
  'email-ja-cadastrado': 'Já existe uma conta com este e-mail.',
  'cpf-ja-cadastrado': 'Já existe uma conta com este CPF.',
  'cpf-invalido': 'CPF inválido. Confira os números.',
  'campos-invalidos': 'Confira os campos destacados.',
  'saldo-insuficiente': 'Saldo insuficiente para esta operação.',
  'limite-diario-excedido': 'Isso passa do seu limite de hoje.',
  'valor-invalido': 'Informe um valor maior que zero.',
  'valor-com-fracao-de-centavo': 'O valor não pode ter mais de dois decimais.',
  'transferencia-para-si': 'A conta de destino é a sua própria.',
  'chave-pix-nao-encontrada': 'Nenhuma conta encontrada para esta chave.',
  'chave-pix-em-uso': 'Esta chave já está registrada em outra conta.',
  'chave-pix-nao-pertence-ao-titular': 'A chave precisa ser sua: seu CPF ou seu e-mail cadastrado.',
  'limite-de-chaves-atingido': 'Você já tem o máximo de 5 chaves.',
  'telefone-invalido': 'Informe o telefone com DDD, só números.',
  'senha-transacional-invalida': 'Senha de movimentação incorreta.',
  'senha-transacional-igual-a-de-acesso': 'Use uma senha diferente da de acesso.',
  'conta-bloqueada': 'Esta conta não aceita movimentação.',
};

export function textoDoErro(falha: unknown): string {
  if (falha instanceof ProblemaDaApi) {
    return TEXTOS[falha.tipo] ?? 'Não foi possível completar a operação. Tente de novo.';
  }
  // Rede fora, servidor dormindo: nao ha `type` nenhum para consultar.
  return 'Sem conexão com o servidor. Verifique sua internet.';
}

export function exigeSenhaTransacional(falha: unknown): boolean {
  return falha instanceof ProblemaDaApi && falha.ehDoTipo('senha-transacional-invalida');
}
