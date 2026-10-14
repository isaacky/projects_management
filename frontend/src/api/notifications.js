import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client.js';

const KEY = ['notifications'];

/** The inbox, refreshed every 30 seconds so new approval requests appear without reloading. */
export function useInbox() {
  return useQuery({
    queryKey: KEY,
    queryFn: ({ signal }) => apiFetch('/api/v1/notifications?limit=20', { signal }),
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
  });
}

export function useMarkRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/api/v1/notifications/${id}/read`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useMarkAllRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch('/api/v1/notifications/read-all', { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
