import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { Layout } from './components/Layout'
import './index.css'
import { BroadcastDetailPage } from './pages/BroadcastDetailPage'
import { BroadcastEditPage, BroadcastNewPage } from './pages/BroadcastFormPage'
import { BroadcastPublishPage } from './pages/BroadcastPublishPage'
import { DonationFailPage, DonationSuccessPage } from './pages/DonationResultPage'
import { EndedBroadcastsPage } from './pages/EndedBroadcastsPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { MyBroadcastsPage } from './pages/MyBroadcastsPage'
import { SignUpPage } from './pages/SignUpPage'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
})

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignUpPage /> },
      { path: '/broadcasts/ended', element: <EndedBroadcastsPage /> },
      { path: '/broadcasts/new', element: <BroadcastNewPage /> },
      { path: '/broadcasts/:id', element: <BroadcastDetailPage /> },
      { path: '/broadcasts/:id/edit', element: <BroadcastEditPage /> },
      { path: '/broadcasts/:id/publish', element: <BroadcastPublishPage /> },
      { path: '/me/broadcasts', element: <MyBroadcastsPage /> },
      { path: '/donations/success', element: <DonationSuccessPage /> },
      { path: '/donations/fail', element: <DonationFailPage /> },
    ],
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
