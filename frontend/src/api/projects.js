import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client.js';

const BASE = '/api/v1/projects';

export const projectKeys = {
  all: ['projects'],
  detail: (id) => ['projects', id],
  history: (id) => ['projects', id, 'history'],
};

export function useProjects() {
  return useQuery({
    queryKey: projectKeys.all,
    queryFn: ({ signal }) => apiFetch(BASE, { signal }),
  });
}

export function useProject(id) {
  return useQuery({
    queryKey: projectKeys.detail(id),
    queryFn: ({ signal }) => apiFetch(`${BASE}/${id}`, { signal }),
    enabled: Boolean(id),
  });
}

export function useProjectHistory(id) {
  return useQuery({
    queryKey: projectKeys.history(id),
    queryFn: ({ signal }) => apiFetch(`${BASE}/${id}/history`, { signal }),
    enabled: Boolean(id),
  });
}

export function useCreateProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input) => apiFetch(BASE, { method: 'POST', body: input }),
    onSuccess: () => qc.invalidateQueries({ queryKey: projectKeys.all }),
  });
}

export function useAdvancePhase(id) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (decisionNote) =>
      apiFetch(`${BASE}/${id}/advance`, { method: 'POST', body: { decisionNote } }),
    onSuccess: (updated) => {
      qc.setQueryData(projectKeys.detail(id), updated);
      qc.invalidateQueries({ queryKey: projectKeys.all });
      qc.invalidateQueries({ queryKey: projectKeys.history(id) });
      // Gates change what closeout allows (e.g. closing the project after G4).
      qc.invalidateQueries({ queryKey: ['closeout', id] });
      // After gate G0 only the PMO changes the objectives, with a reason.
      qc.invalidateQueries({ queryKey: ['objectives', id] });
    },
  });
}
