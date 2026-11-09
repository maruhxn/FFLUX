// Chrome의 프레임 단위 트랙 처리(Insertable Streams for MediaStreamTrack). TypeScript 기본 DOM 타입에 없어 쓰는 만큼만 선언한다.

declare class MediaStreamTrackProcessor {
  constructor(init: { track: MediaStreamTrack })
  readonly readable: ReadableStream<VideoFrame>
}

declare class MediaStreamTrackGenerator extends MediaStreamTrack {
  constructor(init: { kind: 'video' })
  readonly writable: WritableStream<VideoFrame>
}
