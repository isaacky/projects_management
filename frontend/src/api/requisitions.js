import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client.js';

const BASE = '/api/v1/requisitions';

export const requisitionKeys = {
  all: ['requisitions'],
  list: (scope, projectId) => ['requisitions', 'list', scope, projectId ?? null],
  detail: (id) => ['requisitions', id],
  history: (id) => ['requisitions', id, 'history'],
};

/** scope: MINE | AWAITING_ME | ALL */
export function useRequisitions(scope, projectId) {
  const params = new URLSearchParams({ scope });
  if (projectId) params.set('projectId', projectId);
  return useQuery({
    queryKey: requisitionKeys.list(scope, projectId),
    queryFn: ({ signal }) => apiFetch(`${BASE}?${params}`, { signal }),
  });
}

export function useRequisition(id) {
  return useQuery({
    queryKey: requisitionKeys.detail(id),
    queryFn: ({ signal }) => apiFetch(`${BASE}/${id}`, { signal }),
    enabled: Boolean(id),
  });
}

export function useRequisitionHistory(id) {
  return useQuery({
    queryKey: requisitionKeys.history(id),
    queryFn: ({ signal }) => apiFetch(`${BASE}/${id}/history`, { signal }),
    enabled: Boolean(id),
  });
}

function useRequisitionMutation(fn) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (detail) => {
      if (detail?.id) qc.setQueryData(requisitionKeys.detail(detail.id), detail);
      qc.invalidateQueries({ queryKey: requisitionKeys.all });
      qc.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}

export const useSaveRequisition = () =>
  useRequisitionMutation(({ id, ...body }) =>
    id ? apiFetch(`${BASE}/${id}`, { method: 'PUT', body }) : apiFetch(BASE, { method: 'POST', body }),
  );

/** action: submit | approve | reject | return | cancel */
export const useRequisitionAction = (id) =>
  useRequisitionMutation(({ action, comment }) =>
    apiFetch(`${BASE}/${id}/${action}`, {
      method: 'POST',
      body: action === 'submit' ? undefined : { comment: comment || null },
    }),
  );
