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
