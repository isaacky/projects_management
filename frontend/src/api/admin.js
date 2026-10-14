import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client.js';

export const adminKeys = {
  users: ['admin', 'users'],
  roles: ['admin', 'roles'],
};

export function useAdminUsers() {
  return useQuery({
    queryKey: adminKeys.users,
    queryFn: ({ signal }) => apiFetch('/api/v1/admin/users', { signal }),
  });
}

export function useAdminRoles() {
  return useQuery({
    queryKey: adminKeys.roles,
    queryFn: ({ signal }) => apiFetch('/api/v1/admin/roles', { signal }),
    staleTime: 5 * 60_000,
  });
}

function useRefresh() {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ['admin'] });
    qc.invalidateQueries({ queryKey: ['users'] });
    qc.invalidateQueries({ queryKey: ['me'] });
  };
}

/** Adds a person ({ id: undefined }) or saves changes to one. */
export function useSaveUser() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: ({ id, body }) =>
      id
        ? apiFetch(`/api/v1/admin/users/${id}`, { method: 'PUT', body })
        : apiFetch('/api/v1/admin/users', { method: 'POST', body }),
    onSuccess: refresh,
  });
}

export function useUnlinkUser() {
  const refresh = useRefresh();
  return useMutation({
    mutationFn: (id) => apiFetch(`/api/v1/admin/users/${id}/unlink`, { method: 'POST' }),
    onSuccess: refresh,
  });
}
