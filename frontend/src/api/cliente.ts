import { encerrar, guardar, sessaoAtual } from './sessao';

const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

/** Erro de negocio no formato RFC 7807 devolvido pela API. */
export class ProblemaDaApi extends Error {
  readonly tipo: string;
  readonly status: number;
  readonly detalhe: string;
  readonly campos?: Record<string, string>;

  constructor(tipo: string, status: number, detalhe: string, campos?: Record<string, string>) {
    super(detalhe);
    this.name = 'ProblemaDaApi';
    this.tipo = tipo;
    this.status = status;
    this.detalhe = detalhe;
    this.campos = campos;
  }

  /**
   * O `tipo` e estavel; o texto de `detalhe` nao. Toda decisao de
   * comportamento no front olha para isto, nunca para a mensagem.
   */
  ehDoTipo(...tipos: string[]): boolean {
    return tipos.includes(this.tipo);
  }
}

type Opcoes = {
  metodo?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  corpo?: unknown;
  /** Reusar a MESMA chave no retry e o que torna o reenvio seguro. */
  idempotencyKey?: string;
  senhaTransacional?: string;
  autenticada?: boolean;
};

/**
 * Refresh em voo unico.
 *
 * Sem isto, cinco requisicoes que expiram juntas disparam cinco refreshes.
 * O backend rotaciona e revoga o token anterior a cada um, entao o segundo
 * chega com um token ja revogado -- o servidor le isso como roubo e derruba a
 * familia inteira. O usuario e deslogado por ter aberto duas telas ao mesmo
 * tempo. Aqui, quem chega durante um refresh em andamento espera nele.
 */
let refreshEmVoo: Promise<boolean> | null = null;

async function renovar(): Promise<boolean> {
  const sessao = sessaoAtual();
  if (!sessao) return false;

  const resposta = await fetch(`${BASE}/api/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: sessao.refreshToken }),
  });

  if (!resposta.ok) {
    encerrar();
    return false;
  }

  const corpo = await resposta.json();
  guardar({ accessToken: corpo.accessToken, refreshToken: corpo.refreshToken });
  return true;
}

function renovarUmaVezSo(): Promise<boolean> {
  refreshEmVoo ??= renovar().finally(() => {
    refreshEmVoo = null;
  });
  return refreshEmVoo;
}

function cabecalhos(opcoes: Opcoes): HeadersInit {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const sessao = sessaoAtual();

  if (opcoes.autenticada !== false && sessao) {
    headers.Authorization = `Bearer ${sessao.accessToken}`;
  }
  if (opcoes.idempotencyKey) {
    headers['Idempotency-Key'] = opcoes.idempotencyKey;
  }
  if (opcoes.senhaTransacional) {
    headers['X-Senha-Transacional'] = opcoes.senhaTransacional;
  }

  return headers;
}

export async function requisitar<T>(rota: string, opcoes: Opcoes = {}): Promise<T> {
  const enviar = () =>
    fetch(`${BASE}${rota}`, {
      method: opcoes.metodo ?? 'GET',
      headers: cabecalhos(opcoes),
      body: opcoes.corpo === undefined ? undefined : JSON.stringify(opcoes.corpo),
    });

  let resposta = await enviar();

  // Uma tentativa de renovacao, e uma so: se o refresh nao resolveu, insistir
  // vira laco infinito contra um servidor que ja disse nao.
  if (resposta.status === 401 && opcoes.autenticada !== false && sessaoAtual()) {
    if (await renovarUmaVezSo()) {
      resposta = await enviar();
    }
  }

  if (resposta.status === 204) {
    return undefined as T;
  }

  const corpo = await resposta.json().catch(() => null);

  if (!resposta.ok) {
    if (resposta.status === 401 && sessaoAtual()) encerrar();

    const tipo = String(corpo?.title ?? 'erro-desconhecido');
    const detalhe = String(corpo?.detail ?? 'Nao foi possivel completar a operacao.');
    throw new ProblemaDaApi(tipo, resposta.status, detalhe, corpo?.campos);
  }

  return corpo as T;
}
