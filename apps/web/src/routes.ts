/** Screens of the app, by route. Some of them arrive with later use cases. */
export const paths = {
  login: '/',
  inbox: '/caixa-de-tarefas',
  newRequest: '/novo-pedido',
  workflows: '/workflows',
  designer: '/workflows/criar',
  /** A request, by its reference (UC17) */
  request: (reference: string) => `/pedidos/${encodeURIComponent(reference)}`,
} as const
