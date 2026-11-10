import { describe, expect, it } from 'vitest'
import { formatDuration, waitsForRecording, type BroadcastDetail, type Recording } from './broadcast'

describe('formatDuration', () => {
  it('한 시간 미만은 분:초로 쓴다', () => {
    expect(formatDuration(65)).toBe('1:05')
  })

  it('한 시간 이상은 시:분:초로 쓴다', () => {
    expect(formatDuration(3 * 3600 + 7 * 60 + 9)).toBe('3:07:09')
  })
})

describe('waitsForRecording', () => {
  const detail = (status: BroadcastDetail['status'], recording: Recording | null): BroadcastDetail => ({
    id: 1,
    streamerId: 1,
    streamerNickname: '스트리머',
    title: '방송',
    description: null,
    status,
    createdAt: '2026-11-10T00:00:00',
    startedAt: null,
    viewerCount: null,
    whepUrl: null,
    hlsUrl: null,
    recording,
  })
  const processing: Recording = { status: 'PROCESSING', url: null, durationSeconds: null }

  it('끝난 방송의 녹화가 처리 중이면 기다린다', () => {
    expect(waitsForRecording(detail('ENDED', processing), false)).toBe(true)
  })

  it('LIVE를 지켜본 스트리머의 방송이 끝났는데 녹화가 아직 오지 않았으면 기다린다', () => {
    expect(waitsForRecording(detail('ENDED', null), true)).toBe(true)
  })

  it('녹화가 보이지 않으면 시청자이거나 끝난 뒤에 연 화면이므로 기다리지 않는다', () => {
    expect(waitsForRecording(detail('ENDED', null), false)).toBe(false)
  })

  it('녹화 처리가 끝났으면 기다리지 않는다', () => {
    expect(waitsForRecording(detail('ENDED', { status: 'READY', url: '/vod/rec/1/a.mp4', durationSeconds: 60 }), true)).toBe(false)
    expect(waitsForRecording(detail('ENDED', { status: 'FAILED', url: null, durationSeconds: null }), true)).toBe(false)
  })

  it('끝나지 않은 방송은 기다리지 않는다', () => {
    expect(waitsForRecording(detail('LIVE', null), true)).toBe(false)
  })
})
