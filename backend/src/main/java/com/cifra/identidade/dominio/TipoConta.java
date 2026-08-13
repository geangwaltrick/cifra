package com.cifra.identidade.dominio;

public enum TipoConta {

	CORRENTE,

	POUPANCA,

	/**
	 * Fronteira contabil entre o Cifra e o mundo externo. Nao tem titular e e a
	 * unica que pode ficar negativa: seu saldo e, por construcao, o simetrico da
	 * soma de todas as contas de cliente.
	 */
	LIQUIDACAO;

	public boolean ehDeCliente() {
		return this != LIQUIDACAO;
	}

}
