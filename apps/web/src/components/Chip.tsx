import type { ReactNode } from 'react'
import { cx } from '../lib/cx'
import type { Tone } from './Flash'
import { Icon, type IconName } from './Icon'

const tones: Record<Tone, string> = {
  ok: 'bg-success-soft text-success',
  warn: 'bg-warning-soft text-warning',
  bad: 'bg-error-soft text-error',
  info: 'bg-info-soft text-info',
  neutro: 'bg-chip text-ink-2',
}

/** Status chip, sentence case, 26px high */
export function Chip({ tone = 'neutro', icon, children }: { tone?: Tone; icon?: IconName; children: ReactNode }) {
  return (
    <span
      className={cx(
        'edge box-border inline-flex min-h-6.5 items-center gap-1.5 rounded-pill px-2.5 text-[12px] leading-[18px] font-semibold whitespace-nowrap',
        tones[tone],
      )}
    >
      {icon && <Icon name={icon} size={14} />}
      {children}
    </span>
  )
}
