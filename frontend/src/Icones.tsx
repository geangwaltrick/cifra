/**
 * Icones em SVG inline.
 *
 * Inline e nao biblioteca: sao seis desenhos, e uma dependencia inteira para
 * isso pesaria mais que o app. Todos herdam `currentColor`, entao mudam de cor
 * junto com o texto sem regra extra de CSS.
 */

type Props = { tamanho?: number };

const base = (tamanho: number) => ({
  width: tamanho,
  height: tamanho,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
});

export const IconeConta = ({ tamanho = 20 }: Props) => (
  <svg {...base(tamanho)}>
    <rect x="2" y="5" width="20" height="14" rx="2" />
    <path d="M2 10h20" />
  </svg>
);

export const IconePix = ({ tamanho = 20 }: Props) => (
  <svg {...base(tamanho)}>
    <path d="M12 2 22 12 12 22 2 12z" />
    <path d="M8 12h8M12 8v8" />
  </svg>
);

export const IconeCartao = ({ tamanho = 20 }: Props) => (
  <svg {...base(tamanho)}>
    <rect x="2" y="4" width="20" height="16" rx="2.5" />
    <path d="M2 9h20" />
    <path d="M6 15h4" />
  </svg>
);

export const IconeAjustes = ({ tamanho = 20 }: Props) => (
  <svg {...base(tamanho)}>
    <circle cx="12" cy="12" r="3" />
    <path d="M12 2v3M12 19v3M22 12h-3M5 12H2M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1M18.4 18.4l-2.1-2.1M7.7 7.7 5.6 5.6" />
  </svg>
);

export const IconeSair = ({ tamanho = 20 }: Props) => (
  <svg {...base(tamanho)}>
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <path d="M16 17l5-5-5-5M21 12H9" />
  </svg>
);

export const IconeEntrada = ({ tamanho = 16 }: Props) => (
  <svg {...base(tamanho)}>
    <path d="M12 5v14M19 12l-7 7-7-7" />
  </svg>
);

export const IconeSaida = ({ tamanho = 16 }: Props) => (
  <svg {...base(tamanho)}>
    <path d="M12 19V5M5 12l7-7 7 7" />
  </svg>
);

export const IconeOlho = ({ tamanho = 18 }: Props) => (
  <svg {...base(tamanho)}>
    <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
);

export const IconeOlhoFechado = ({ tamanho = 18 }: Props) => (
  <svg {...base(tamanho)}>
    <path d="M3 3l18 18" />
    <path d="M10.6 6.2A9.9 9.9 0 0 1 12 6c6.4 0 10 7 10 7a17 17 0 0 1-3.2 4M6.3 7.5A17 17 0 0 0 2 13s3.6 7 10 7a9.7 9.7 0 0 0 4.6-1.1" />
  </svg>
);
