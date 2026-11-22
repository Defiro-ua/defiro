import { describe, expect, it } from 'vitest'
import { canMove, canMoveField, changeDraft, fieldProblem, problemsOf, totalDeadline, type Draft, type Field, type Step } from './draft'

const submission = { name: 'submission', version: '0.1.0' }
const review = { name: 'review', version: '0.1.0' }
const finalValidation = { name: 'final-validation', version: '0.1.0' }

const question = (id: string, label: string, more: Partial<Field> = {}): Field => ({
  id,
  label,
  type: 'short-text',
  required: true,
  options: [],
  ...more,
})

const material = question('material', 'Material pedido')
const NL = String.fromCharCode(10)

const request: Step = {
  id: 'pedido',
  plugin: submission,
  name: 'Pedido',
  handledBy: 'Todos os trabalhadores',
  deadline: null,
  returnReasons: [],
  rejectReasons: [],
  fields: [material],
}

function reviewStep(id: string, name: string): Step {
  return {
    id,
    plugin: review,
    name,
    handledBy: 'Chefias da unidade do requerente',
    deadline: 5,
    returnReasons: ['Preenchido incorretamente'],
    rejectReasons: ['Pedido não autorizado'],
    fields: [],
  }
}

const draft = (...steps: Step[]): Draft => ({ name: 'Ajudas de custo', steps: [request, ...steps] })
const path = (d: Draft) => d.steps.map((s) => s.name)

describe('changing a draft', () => {
  it('renames the workflow', () => {
    expect(changeDraft(draft(), { t: 'name', v: 'Horas extraordinárias' }).name).toBe('Horas extraordinárias')
  })

  it('adds a review step at the end, with a reason of each kind to start from', () => {
    const changed = changeDraft(draft(), { t: 'addStep', id: 'n2', plugin: review })

    expect(path(changed)).toEqual(['Pedido', 'Revisão'])
    expect(changed.steps[1]).toMatchObject({
      id: 'n2',
      plugin: review,
      handledBy: null,
      deadline: null,
      returnReasons: ['Preenchido incorretamente'],
      rejectReasons: ['Pedido não autorizado'],
      fields: [],
    })
  })

  it('keeps the final validation last, and alone', () => {
    let d = changeDraft(draft(reviewStep('n2', 'Validação da chefia')), { t: 'addStep', id: 'f', plugin: finalValidation })
    d = changeDraft(d, { t: 'addStep', id: 'n3', plugin: review })
    d = changeDraft(d, { t: 'addStep', id: 'f2', plugin: finalValidation })

    expect(path(d)).toEqual(['Pedido', 'Validação da chefia', 'Revisão', 'Validação final'])
  })

  it('moves a review step up or down among the review steps', () => {
    const d = draft(reviewStep('a', 'Validação da chefia'), reviewStep('b', 'Verificação financeira'))

    expect(path(changeDraft(d, { t: 'moveStep', step: 'b', by: -1 }))).toEqual(['Pedido', 'Verificação financeira', 'Validação da chefia'])
    expect(path(changeDraft(d, { t: 'moveStep', step: 'a', by: 1 }))).toEqual(['Pedido', 'Verificação financeira', 'Validação da chefia'])
  })

  it('never moves a step above the request or below the final validation, nor the request itself', () => {
    let d = draft(reviewStep('a', 'Validação da chefia'))
    d = changeDraft(d, { t: 'addStep', id: 'f', plugin: finalValidation })

    expect(path(changeDraft(d, { t: 'moveStep', step: 'a', by: -1 }))).toEqual(path(d))
    expect(path(changeDraft(d, { t: 'moveStep', step: 'a', by: 1 }))).toEqual(path(d))
    expect(path(changeDraft(d, { t: 'moveStep', step: 'pedido', by: 1 }))).toEqual(path(d))
    expect(path(changeDraft(d, { t: 'moveStep', step: 'f', by: -1 }))).toEqual(path(d))
  })

  it('says which moves the path allows, for the buttons to offer only those', () => {
    const d = draft(reviewStep('a', 'Validação da chefia'), reviewStep('b', 'Verificação financeira'))

    expect(canMove(d.steps, 'a', -1)).toBe(false)
    expect(canMove(d.steps, 'a', 1)).toBe(true)
    expect(canMove(d.steps, 'b', -1)).toBe(true)
    expect(canMove(d.steps, 'b', 1)).toBe(false)
  })

  it('removes a step, but never the request', () => {
    const d = draft(reviewStep('a', 'Validação da chefia'), reviewStep('b', 'Verificação financeira'))

    expect(path(changeDraft(d, { t: 'removeStep', step: 'b' }))).toEqual(['Pedido', 'Validação da chefia'])
    expect(path(changeDraft(d, { t: 'removeStep', step: 'pedido' }))).toEqual(path(d))
  })

  it('changes the name and who handles a step', () => {
    let d = changeDraft(draft(reviewStep('a', 'Validação da chefia')), { t: 'step', step: 'a', k: 'name', v: 'Validação' })
    d = changeDraft(d, { t: 'step', step: 'a', k: 'handledBy', v: 'Divisão Financeira' })

    expect(d.steps[1]).toMatchObject({ name: 'Validação', handledBy: 'Divisão Financeira' })
  })

  it('keeps only the digits of a deadline, and no deadline when there are none', () => {
    const d = draft(reviewStep('a', 'Validação da chefia'))

    expect(changeDraft(d, { t: 'deadline', step: 'a', v: '1a2' }).steps[1].deadline).toBe(12)
    expect(changeDraft(d, { t: 'deadline', step: 'a', v: '' }).steps[1].deadline).toBeNull()
  })

  it('adds, renames and removes the reasons of each list on their own', () => {
    let d = draft(reviewStep('a', 'Validação da chefia'))
    d = changeDraft(d, { t: 'addReason', step: 'a', list: 'rejectReasons' })
    d = changeDraft(d, { t: 'renameReason', step: 'a', list: 'rejectReasons', index: 1, v: 'Fora de prazo' })
    d = changeDraft(d, { t: 'removeReason', step: 'a', list: 'returnReasons', index: 0 })

    expect(d.steps[1].returnReasons).toEqual([])
    expect(d.steps[1].rejectReasons).toEqual(['Pedido não autorizado', 'Fora de prazo'])
  })
})

describe('the problems of a draft', () => {
  it('has none when every answer is there', () => {
    expect(problemsOf(draft(reviewStep('a', 'Validação da chefia')))).toEqual([])
  })

  it('names the workflow, as the server does', () => {
    expect(problemsOf({ ...draft(reviewStep('a', 'Validação da chefia')), name: '  ' })).toEqual([{ field: 'name', message: 'Dê um nome ao workflow.' }])
  })

  it('asks for who can make the request', () => {
    const d = draft(reviewStep('a', 'Validação da chefia'))
    expect(problemsOf(changeDraft(d, { t: 'step', step: 'pedido', k: 'handledBy', v: '' }))).toEqual([
      { field: 'steps[0].handledBy', message: 'Passo 1 («Pedido»): escolha quem trata.' },
    ])
  })

  it('asks a step for its name, who handles it and its deadline', () => {
    const step = { ...reviewStep('a', ''), handledBy: null, deadline: null }

    expect(problemsOf(draft(step))).toEqual([
      { field: 'steps[1].name', message: 'Passo 2: falta o nome.' },
      { field: 'steps[1].handledBy', message: 'Passo 2: escolha quem trata.' },
      { field: 'steps[1].deadline', message: 'Passo 2: indique o prazo legal, de 1 a 90 dias úteis.' },
    ])
  })

  it('takes a deadline from 1 to 90 working days', () => {
    const at = (deadline: number) => problemsOf(draft({ ...reviewStep('a', 'Validação da chefia'), deadline }))

    expect(at(1)).toEqual([])
    expect(at(90)).toEqual([])
    expect(at(91)).toEqual([{ field: 'steps[1].deadline', message: 'Passo 2 («Validação da chefia»): o prazo legal tem de ser de 1 a 90 dias úteis.' }])
    expect(at(0)).toHaveLength(1)
  })

  it('asks for at least one reason, and a name for each, counted in its own list', () => {
    const none = { ...reviewStep('a', 'Validação da chefia'), returnReasons: [], rejectReasons: [] }
    const unnamed = { ...reviewStep('a', 'Validação da chefia'), returnReasons: [''], rejectReasons: ['Pedido não autorizado', ' '] }

    expect(problemsOf(draft(none))).toEqual([
      { field: 'steps[1].reasons', message: 'Passo 2 («Validação da chefia»): acrescente pelo menos um motivo para devolver ou rejeitar.' },
    ])
    expect(problemsOf(draft(unnamed))).toEqual([
      { field: 'steps[1].returnReasons[0]', message: 'Passo 2 («Validação da chefia»): dê um nome ao motivo de devolução 1.' },
      { field: 'steps[1].rejectReasons[1]', message: 'Passo 2 («Validação da chefia»): dê um nome ao motivo de rejeição 2.' },
    ])
  })
})

const asking = (...fields: Field[]): Draft => {
  const d = draft(reviewStep('a', 'Validação da chefia'))
  return { ...d, steps: [{ ...request, fields }, ...d.steps.slice(1)] }
}
const labels = (d: Draft) => d.steps[0].fields.map((f) => f.label)

describe('the questions of the request form', () => {
  it('adds a question at the end: a short text, optional, to be named on its own screen', () => {
    const d = changeDraft(asking(material), { t: 'addField', id: 'q2' })

    expect(d.steps[0].fields).toEqual([material, { id: 'q2', label: '', type: 'short-text', required: false, options: [] }])
  })

  it('changes the label, the answer type and whether it is mandatory', () => {
    let d = changeDraft(asking(material), { t: 'field', field: 'material', patch: { label: 'Transporte' } })
    d = changeDraft(d, { t: 'field', field: 'material', patch: { type: 'single-choice', required: false } })

    expect(d.steps[0].fields[0]).toEqual({ ...material, label: 'Transporte', type: 'single-choice', required: false })
  })

  it('takes the options of a single choice one per line, keeping the line being written', () => {
    const d = changeDraft(asking(question('t', 'Transporte', { type: 'single-choice' })), { t: 'options', field: 't', v: 'Carro' + NL + 'Comboio' + NL })

    expect(d.steps[0].fields[0].options).toEqual(['Carro', 'Comboio', ''])
  })

  it('moves a question up or down, and says which moves the form allows', () => {
    const d = asking(question('a', 'Local da deslocação'), question('b', 'Transporte'))

    expect(labels(changeDraft(d, { t: 'moveField', field: 'b', by: -1 }))).toEqual(['Transporte', 'Local da deslocação'])
    expect(labels(changeDraft(d, { t: 'moveField', field: 'a', by: -1 }))).toEqual(labels(d))
    expect(labels(changeDraft(d, { t: 'moveField', field: 'b', by: 1 }))).toEqual(labels(d))
    expect([canMoveField(d, 'a', -1), canMoveField(d, 'a', 1), canMoveField(d, 'b', -1), canMoveField(d, 'b', 1)]).toEqual([false, true, true, false])
  })

  it('removes a question', () => {
    const d = asking(question('a', 'Local da deslocação'), question('b', 'Transporte'))

    expect(labels(changeDraft(d, { t: 'removeField', field: 'a' }))).toEqual(['Transporte'])
  })

  it('asks the requester at least one question, as the server does', () => {
    expect(problemsOf(asking())).toEqual([
      {
        field: 'steps[0].fields',
        message: 'Formulário do pedido: ainda não tem perguntas para quem faz o pedido… crie pelo menos uma pergunta, por exemplo «Material pedido»',
      },
    ])
  })

  it('asks each question for a label and a single choice for two options, as the server does', () => {
    const d = asking(material, question('q2', ' '), question('q3', 'Transporte', { type: 'single-choice', options: ['Carro', ' '] }))

    expect(problemsOf(d)).toEqual([
      { field: 'steps[0].fields[1].label', message: 'Formulário do pedido, campo 2: falta o rótulo.' },
      { field: 'steps[0].fields[2].options', message: 'Formulário do pedido, campo 3 («Transporte»): faltam opções de resposta (pelo menos duas).' },
    ])
    expect(problemsOf(asking(question('q3', 'Transporte', { type: 'single-choice', options: ['Carro', 'Comboio'] })))).toEqual([])
  })

  it('says in a few words what is wrong with a question, for the list of questions', () => {
    expect(fieldProblem(question('q', ''))).toBe('Falta o rótulo')
    expect(fieldProblem(question('q', 'Transporte', { type: 'single-choice', options: ['Carro'] }))).toBe('Faltam opções de resposta (pelo menos duas)')
    expect(fieldProblem(question('q', 'Transporte', { type: 'single-choice', options: ['Carro', 'Comboio'] }))).toBe('')
    expect(fieldProblem(material)).toBe('')
  })
})

describe('the total deadline', () => {
  it('adds the deadlines of every step', () => {
    expect(totalDeadline(draft(reviewStep('a', 'A'), { ...reviewStep('b', 'B'), deadline: 10 }, { ...reviewStep('c', 'C'), deadline: null }))).toBe(15)
  })
})
