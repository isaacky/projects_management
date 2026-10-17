import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client.js';
import { projectKeys } from './projects.js';
import { benefitKeys } from './benefits.js';

export const objectiveKeys = {
  project: (projectId) => ['objectives', projectId],
};

export function useObjectives(projectId) {
  return useQuery({
    queryKey: objectiveKeys.project(projectId),
    queryFn: ({ signal }) => apiFetch(`/api/v1/projects/${projectId}/objectives`, { signal }),
    enabled: Boolean(projectId),
  });
}

/**
 * Any change to a project's objectives; every endpoint returns the objectives view.
 * vars: { path: '' | '/<id>' | '/<id>/withdraw' | '/<id>/reinstate', method, body }
 */
export function useObjectiveAction(projectId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ path = '', method = 'POST', body }) =>
      apiFetch(`/api/v1/projects/${projectId}/objectives${path}`, {
        method,
        body: method === 'DELETE' ? undefined : (body ?? {}),
      }),
    onSuccess: (view) => {
      if (view?.projectId) qc.setQueryData(objectiveKeys.project(projectId), view);
      qc.invalidateQueries({ queryKey: projectKeys.history(projectId) });
      // The review form lists the active objectives.
      qc.invalidateQueries({ queryKey: benefitKeys.project(projectId) });
    },
  });
}
