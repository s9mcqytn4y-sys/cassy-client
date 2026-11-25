# Multi-stage build for Next.js App Router (standalone output)
FROM node:22-alpine AS base
ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable && corepack prepare pnpm@12.18.0 --activate

FROM base AS dependencies
WORKDIR /app
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY apps/web/package.json ./apps/web/
COPY packages/design-tokens/package.json ./packages/design-tokens/
RUN pnpm install --frozen-lockfile

FROM dependencies AS builder
WORKDIR /app
COPY packages/design-tokens ./packages/design-tokens
COPY apps/web ./apps/web

ENV NEXT_TELEMETRY_DISABLED=1
ENV NODE_ENV=production
ENV BUILD_STANDALONE=true
ARG NEXT_PUBLIC_APP_URL
ENV NEXT_PUBLIC_APP_URL=$NEXT_PUBLIC_APP_URL
# Marketing pages read prices from the API at build (FL-Q6). Without PLANS_API_URL the build fails unless
# ALLOW_PLAN_FALLBACK=1 (CI validation builds only).
ARG PLANS_API_URL
ARG ALLOW_PLAN_FALLBACK
RUN PLANS_API_URL=$PLANS_API_URL ALLOW_PLAN_FALLBACK=$ALLOW_PLAN_FALLBACK pnpm --filter @azuredevid/cassy-web build

FROM node:22-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"
ENV NEXT_TELEMETRY_DISABLED=1
# Daily revalidation of the marketing pages reads prices from here; on failure Next keeps the last good page.
ARG PLANS_API_URL
ENV PLANS_API_URL=$PLANS_API_URL

RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nextjs

COPY --from=builder /app/apps/web/public ./apps/web/public
COPY --from=builder --chown=nextjs:nodejs /app/apps/web/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/apps/web/.next/static ./apps/web/.next/static

USER nextjs

EXPOSE 3000

CMD ["node", "apps/web/server.js"]
