import { describe, expect, it } from 'vitest'
import { amountError } from './donations'

describe('amountError', () => {
  it('1,000원부터 1,000,000원까지 받는다', () => {
    expect(amountError(1_000)).toBeNull()
    expect(amountError(1_000_000)).toBeNull()
  })

  it('범위 밖이나 정수가 아닌 금액은 거절한다', () => {
    for (const amount of [999, 1_000_001, 0, -1_000, 1_500.5, Number.NaN]) {
      expect(amountError(amount)).not.toBeNull()
    }
  })
})
