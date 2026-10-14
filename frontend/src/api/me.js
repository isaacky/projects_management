import { useQuery } from '@tanstack/react-query';
import { apiFetch, DEV_MODE } from './client.js';

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: ({ signal }) => apiFetch('/api/v1/me', { signal }),
    staleTime: 5 * 60_000,
  });
}

/** Active users of the organisation. */
export function useUsers() {
  return useQuery({
    queryKey: ['users'],
    queryFn: ({ signal }) => apiFetch('/api/v1/users', { signal }),
    staleTime: 5 * 60_000,
  });
}

/** Development only: users for the "Viewing as" switcher. */
export function useDevUsers() {
  return useQuery({
    queryKey: ['dev-users'],
    queryFn: ({ signal }) => apiFetch('/api/v1/dev/users', { signal }),
    enabled: DEV_MODE,
    staleTime: 5 * 60_000,
  });
}

export function hasRole(me, role) {
  return Boolean(me?.roles?.includes(role));
}

export const PROCUREMENT_ROLES = ['PROCUREMENT_OFFICER', 'HEAD_OF_PROCUREMENT'];

export function isProcurement(me) {
  return PROCUREMENT_ROLES.some((r) => hasRole(me, r));
}
