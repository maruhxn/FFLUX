import { useMutation } from '@tanstack/react-query'
import { api } from '../api/client'

/** 금액 범위와 메시지 길이. 서버 값 객체(`DonationAmount`, `DonationMessage`)와 같다. */
export const DONATION_MIN = 1_000
export const DONATION_MAX = 1_000_000
export const DONATION_MESSAGE_MAX_LENGTH = 100
export const DONATION_PRESETS = [1_000, 5_000, 10_000] as const

/** 토스페이먼츠 결제위젯 클라이언트 키. 비밀값이 아니다. 비어 있으면 후원 결제를 열 수 없다. */
export const TOSS_CLIENT_KEY: string = import.meta.env.VITE_TOSS_CLIENT_KEY ?? ''

/** 결제창에 넘길 주문. 확정·취소 응답도 같은 모양이다. */
export type DonationOrder = { orderId: string; broadcastId: number; amount: number }

/** 직접 입력한 금액의 오류. 정수 원 단위로 1,000~1,000,000원이어야 한다. 맞으면 null. */
export function amountError(amount: number): string | null {
  if (!Number.isInteger(amount) || amount < DONATION_MIN || amount > DONATION_MAX) {
    return `후원 금액은 ${formatWon(DONATION_MIN)} 이상 ${formatWon(DONATION_MAX)} 이하입니다.`
  }
  return null
}

export const formatWon = (amount: number) => `${amount.toLocaleString('ko-KR')}원`

/** 결제 전 PENDING 후원을 만든다. 대상은 서버가 방송의 스트리머로 정한다. */
export function useRequestDonation(broadcastId: number) {
  return useMutation({
    mutationFn: (input: { amount: number; message: string }) =>
      api<DonationOrder>(`/api/broadcasts/${broadcastId}/donations`, { method: 'POST', body: input }),
  })
}

/** 결제창 승인 뒤(successUrl) 서버가 토스 승인 API로 확정한다. 같은 주문을 다시 확정해도 성공한다. */
export function useConfirmDonation() {
  return useMutation({
    mutationFn: ({ orderId, paymentKey, amount }: { orderId: string; paymentKey: string; amount: number }) =>
      api<DonationOrder>(`/api/donations/${encodeURIComponent(orderId)}/confirm`, { method: 'POST', body: { paymentKey, amount } }),
  })
}

/** 결제창에서 실패·취소해 돌아오면(failUrl) 후원을 취소한다. */
export function useCancelDonation() {
  return useMutation({
    mutationFn: (orderId: string) => api<DonationOrder>(`/api/donations/${encodeURIComponent(orderId)}/cancel`, { method: 'POST' }),
  })
}
