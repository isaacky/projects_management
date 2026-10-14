import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client.js';

export const benefitKeys = {
  project: (projectId) => ['benefits', 'project', projectId],
  portfolio: ['benefits', 'portfolio'],
};

export function useProjectBenefits(projectId) {
  return useQuery({
    queryKey: benefitKeys.project(projectId),
    queryFn: ({ signal }) => apiFetch(`/api/v1/projects/${projectId}/benefits`, { signal }),
    enabled: Boolean(projectId),
  });
}

export function useBenefitsPortfolio() {
  return useQuery({
    queryKey: benefitKeys.portfolio,
    queryFn: ({ signal }) => apiFetch('/api/v1/benefits', { signal }),
  });
}

/**
 * Any change on a project's benefits or reviews; every endpoint returns the project's benefits view.
 * vars: { path: '/benefits' | '/benefits/<id>/measurements' | '/benefit-reviews/<id>/complete' ..., method, body }
 */
export function useBenefitAction(projectId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ path, method = 'POST', body }) =>
      apiFetch(`/api/v1/projects/${projectId}${path}`, {
        method,
        body: method === 'DELETE' ? undefined : (body ?? {}),
      }),
    onSuccess: (view) => {
      if (view?.projectId) qc.setQueryData(benefitKeys.project(projectId), view);
      qc.invalidateQueries({ queryKey: benefitKeys.portfolio });
      qc.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}
