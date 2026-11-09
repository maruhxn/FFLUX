# 운영용 Caddy 이미지: 클라이언트 빌드 결과(dist)를 /srv에 담는다. 저장소 루트의 compose.yml이 빌드한다.
FROM node:24-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM caddy:2
COPY --from=build /workspace/dist /srv
