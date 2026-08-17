const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

export type EstadoDoServidor = 'verificando' | 'acordando' | 'pronto' | 'indisponivel';

/**
 * Em camada gratuita o container hiberna depois de alguns minutos parado e
 * leva dezenas de segundos para voltar. O front e estatico e carrega na hora,
 * entao a tela aparece pronta enquanto a API ainda esta subindo -- e o
 * primeiro clique falha sem explicacao.
 *
 * Sondar a saude no carregamento resolve o pior do problema: em vez de uma
 * tela que parece quebrada, a pessoa ve que algo esta acontecendo e espera.
 * Um aviso honesto compra mais paciencia do que qualquer spinner.
 */
export async function aguardarServidor(
  aoMudar: (estado: EstadoDoServidor) => void,
  tentativas = 20,
): Promise<void> {
  aoMudar('verificando');

  for (let tentativa = 0; tentativa < tentativas; tentativa++) {
    try {
      const resposta = await fetch(`${BASE}/actuator/health`, { signal: AbortSignal.timeout(8000) });

      // 503 tambem conta como vivo: o processo respondeu, algum componente e
      // que ainda esta subindo. So a ausencia de resposta significa dormindo.
      if (resposta.ok || resposta.status === 503) {
        aoMudar('pronto');
        return;
      }
    } catch {
      // Sem resposta: container hibernando, ou rede fora. Segue tentando.
    }

    // A primeira falha ja e sinal de cold start -- avisa antes de insistir,
    // senao a pessoa encara varios segundos de nada.
    aoMudar('acordando');

    // Espera crescente ate 5s: sondar de meio em meio segundo nao acorda mais
    // rapido e so gasta a cota do plano gratuito.
    await new Promise((resolva) => setTimeout(resolva, Math.min(1000 * (tentativa + 1), 5000)));
  }

  aoMudar('indisponivel');
}
