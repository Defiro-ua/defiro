/**
 * The groups and units a step can be handled by, and who can make a request. Until UC05 reads them
 * from the organization's directory (the identity provider), they are the prototype's list.
 */
export const groups: Array<{ nome: string; nota: string }> = [
  { nome: 'Todos os trabalhadores', nota: 'Grupo do IdP · 1 240 pessoas' },
  { nome: 'Chefias da unidade do requerente', nota: 'Calculado a partir da unidade de quem faz o pedido' },
  { nome: 'Chefias', nota: 'Grupo do IdP · 86 pessoas' },
  { nome: 'Divisão de Obras Municipais', nota: 'Unidade do IdP · 48 pessoas' },
  { nome: 'Divisão Financeira', nota: 'Unidade do IdP · 6 pessoas' },
  { nome: 'Recursos Humanos', nota: 'Unidade do IdP · 12 pessoas' },
  { nome: 'RH · Formação', nota: 'Grupo do IdP · 4 pessoas' },
]
