import { describe, expect, it } from 'vitest'
import { formatRemaining } from './remaining'

describe('formatRemaining', () => {
  it('1시간 안은 분:초로 보여 주고 남은 초는 올림한다', () => {
    expect(formatRemaining(30_000)).toBe('0:30')
    expect(formatRemaining(24_001)).toBe('0:25')
    expect(formatRemaining(59 * 60_000 + 5_000)).toBe('59:05')
  })

  it('1시간부터는 시:분:초로 보여 준다', () => {
    expect(formatRemaining(60 * 60_000)).toBe('1:00:00')
  })

  it('지난 시간은 0:00이다', () => {
    expect(formatRemaining(-1_000)).toBe('0:00')
  })
})
