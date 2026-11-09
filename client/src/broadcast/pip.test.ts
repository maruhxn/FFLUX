import { describe, expect, it } from 'vitest'
import { pipRect } from './pip'

describe('pipRect', () => {
  it('캠을 화면 가로·세로 1/4 상자 안에 비율대로 맞춰 우하단에 둔다', () => {
    expect(pipRect(1920, 1080, 1280, 720)).toEqual({ x: 1408, y: 778, width: 480, height: 270 })
  })

  it('세로로 긴 캠은 높이에 맞춘다', () => {
    expect(pipRect(1920, 1080, 720, 1280)).toEqual({ x: 1736, y: 778, width: 152, height: 270 })
  })

  it('여백은 화면의 짧은 변에 비례한다', () => {
    expect(pipRect(1280, 720, 640, 360)).toEqual({ x: 938, y: 518, width: 320, height: 180 })
  })
})
