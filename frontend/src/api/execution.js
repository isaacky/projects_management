import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client.js';

const p = (projectId) => `/api/v1/projects/${projectId}`;

export function usePortfolioHealth() {
  return useQuery({
    queryKey: ['portfolio-health'],
    queryFn: ({ signal }) => apiFetch('/api/v1/portfolio/health', { signal }),
  });
}

function useList(projectId, kind) {
  return useQuery({
    queryKey: ['projects', projectId, kind],
    queryFn: ({ signal }) => apiFetch(`${p(projectId)}/${kind}`, { signal }),
    enabled: Boolean(projectId),
  });
}

function useListMutation(projectId, kind, fn) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['projects', projectId, kind] });
      qc.invalidateQueries({ queryKey: ['projects', projectId, 'history'] });
      qc.invalidateQueries({ queryKey: ['portfolio-health'] });
      if (kind !== 'status-reports') qc.invalidateQueries({ queryKey: ['projects', projectId, 'status-reports'] });
    },
  });
}

export const useRisks = (projectId) => useList(projectId, 'risks');
export const useIssues = (projectId) => useList(projectId, 'issues');
export const useStatusReports = (projectId) => useList(projectId, 'status-reports');

/** { id?, ...body } creates or updates. */
export const useSaveRisk = (projectId) =>
  useListMutation(projectId, 'risks', ({ id, ...body }) =>
    apiFetch(id ? `${p(projectId)}/risks/${id}` : `${p(projectId)}/risks`, { method: id ? 'PUT' : 'POST', body }),
  );

export const useCloseRisk = (projectId) =>
  useListMutation(projectId, 'risks', ({ id, note, reopen }) =>
    apiFetch(`${p(projectId)}/risks/${id}/${reopen ? 'reopen' : 'close'}`, { method: 'POST', body: { note } }),
  );

export const useSaveIssue = (projectId) =>
  useListMutation(projectId, 'issues', ({ id, ...body }) =>
    apiFetch(id ? `${p(projectId)}/issues/${id}` : `${p(projectId)}/issues`, { method: id ? 'PUT' : 'POST', body }),
  );

export const useCreateReport = (projectId) =>
  useListMutation(projectId, 'status-reports', (periodEnd) =>
    apiFetch(`${p(projectId)}/status-reports`, { method: 'POST', body: { periodEnd } }),
  );

export const useEditReport = (projectId) =>
  useListMutation(projectId, 'status-reports', ({ id, ...body }) =>
    apiFetch(`${p(projectId)}/status-reports/${id}`, { method: 'PUT', body }),
  );

/** action: submit | review */
export const useReportAction = (projectId) =>
  useListMutation(projectId, 'status-reports', ({ id, action, note }) =>
    apiFetch(`${p(projectId)}/status-reports/${id}/${action}`, { method: 'POST', body: { note } }),
  );
