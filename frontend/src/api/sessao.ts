const CHAVE = 'cifra.sessao';

export type Sessao = {
  accessToken: string;
  refreshToken: string;
};

type Ouvinte = (sessao: Sessao | null) => void;

let atual: Sessao | null = carregar();
const ouvintes = new Set<Ouvinte>();

function carregar(): Sessao | null {
  try {
    const bruto = localStorage.getItem(CHAVE);
    return bruto ? (JSON.parse(bruto) as Sessao) : null;
  } catch {
    return null;
  }
}

export function sessaoAtual(): Sessao | null {
  return atual;
}

export function guardar(sessao: Sessao): void {
  atual = sessao;
  localStorage.setItem(CHAVE, JSON.stringify(sessao));
  ouvintes.forEach((ouvinte) => ouvinte(sessao));
}

export function encerrar(): void {
  atual = null;
  localStorage.removeItem(CHAVE);
  ouvintes.forEach((ouvinte) => ouvinte(null));
}

export function observar(ouvinte: Ouvinte): () => void {
  ouvintes.add(ouvinte);
  return () => ouvintes.delete(ouvinte);
}

/**
 * O primeiro nome do titular, lido do proprio access token.
 *
 * O token ja carrega a claim `nome` -- usar isso poupa uma requisicao so para
 * escrever "boa tarde, Ana". Ler o corpo de um JWT no cliente e legitimo para
 * exibicao: ele nao e cifrado, so assinado. O que nao se pode e *confiar* nele
 * para decidir permissao, e nada aqui decide nada; quem verifica a assinatura
 * e o servidor, a cada chamada.
 */
export function primeiroNome(): string | null {
  const token = atual?.accessToken;
  if (!token) return null;

  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')), (c) =>
      c.charCodeAt(0),
    );

    // TextDecoder e nao atob direto: "Joao Gonçalves" vem em UTF-8 e viraria
    // mojibake se cada byte fosse lido como um caractere.
    const { nome } = JSON.parse(new TextDecoder().decode(bytes)) as { nome?: string };

    return nome?.trim().split(/\s+/)[0] ?? null;
  } catch {
    return null;
  }
}
