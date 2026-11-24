import { describe, expect, it } from 'vitest'
import type { Draft, Step } from './draft'
import { domId, kindOf, numberOf, ofPath, screenOfField, screenOrder } from './screens'

const step = (id: string, plugin: string): Step => ({
  id,
  plugin: { name: plugin, version: '0.1.0' },
  name: id,
  handledBy: null,
  deadline: null,
  returnReasons: [],
  rejectReasons: [],
})

const draft: Draft = { name: 'Ajudas de custo', steps: [step('pedido', 'submission'), step('a', 'review'), step('f', 'final-validation')] }

describe('the designer screens', () => {
  it('go through the name, who can request, three screens per step, the path and the review', () => {
    expect(screenOrder(draft)).toEqual(['ident', 'quem', 'pn:a', 'pp:a', 'pm:a', 'pn:f', 'pp:f', 'pm:f', 'percurso', 'rever'])
  })

  it('are numbered out of four, the steps counting as the path', () => {
    expect(['ident', 'quem', 'pn:a', 'pm:a', 'percurso', 'rever'].map((s) => numberOf(kindOf(s).kind))).toEqual([1, 2, 3, 3, 3, 4])
  })

  it('know which screen answers each field of a problem', () => {
    expect(screenOfField(draft, 'name')).toBe('ident')
    expect(screenOfField(draft, 'steps[0].handledBy')).toBe('quem')
    expect(screenOfField(draft, 'steps[1].name')).toBe('pn:a')
    expect(screenOfField(draft, 'steps[1].plugin')).toBe('pn:a')
    expect(screenOfField(draft, 'steps[1].deadline')).toBe('pp:a')
    expect(screenOfField(draft, 'steps[2].rejectReasons[0]')).toBe('pm:f')
    expect(screenOfField(draft, 'steps')).toBe('percurso')
    expect(screenOfField(draft, 'steps[9].name')).toBe('percurso')
  })

  it('tell the path screens apart', () => {
    expect(['pn:a', 'pp:a', 'pm:a', 'percurso', 'ident', 'quem', 'rever'].map(ofPath)).toEqual([true, true, true, true, false, false, false])
  })

  it('give each field an element id', () => {
    expect(domId('name')).toBe('f-name')
    expect(domId('steps[1].returnReasons[0]')).toBe('f-steps-1-returnReasons-0')
  })
})
