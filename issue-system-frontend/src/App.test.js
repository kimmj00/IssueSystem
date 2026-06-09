import { fireEvent, render, screen } from '@testing-library/react';
import App from './App';

function apiResponse(data) {
  return Promise.resolve({
    ok: true,
    json: () => Promise.resolve({ success: true, data }),
  });
}

beforeEach(() => {
  window.history.replaceState(null, '', '/');
  jest.spyOn(window, 'open').mockImplementation(() => null);

  global.fetch = jest.fn((url) => {
    if (url.includes('/api/global-search')) {
      return apiResponse({});
    }

    if (url.includes('/api/work-issue-histories/uploads')) {
      return apiResponse([]);
    }

    if (url.includes('/api/work-issue-histories/summary')) {
      return apiResponse({});
    }

    if (url.includes('/api/work-issue-histories/projects')) {
      return apiResponse([
        {
          id: 1,
          no: 2,
          clientName: '법무부',
          siteCode: 'A25427',
          projectScale: '단순구축',
          salesRep: '서은숙',
          executors: '최승훈',
          startDate: '2025-07-09',
          scope: 'SMS(42/70)\nNMS(81/230)',
          visits: 1,
          md: 2.6,
          progressLogs: '4/27(방문)\n프로젝트 재시작',
          remainingIssues: '담당자 전부 바뀜',
        },
      ]);
    }

    if (url.includes('/api/work-issue-histories/maintenance')) {
      return apiResponse([]);
    }

    return apiResponse({});
  });
});

afterEach(() => {
  jest.restoreAllMocks();
});

test('작업 및 이슈이력 행 클릭 시 목록 아래에 상세 내용을 펼친다', async () => {
  render(<App />);

  fireEvent.click(screen.getByRole('button', { name: /작업 및 이슈이력/ }));
  const customerName = await screen.findByText('법무부(A25427)');
  const popupButton = screen.getByRole('button', { name: '상세 팝업 열기' });

  fireEvent.click(popupButton);
  expect(window.open).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('금주 실적 및 진행 내역 (누적)')).not.toBeInTheDocument();

  fireEvent.click(customerName);

  expect(await screen.findByText('금주 실적 및 진행 내역 (누적)')).toBeInTheDocument();
  expect(screen.getByText('잔여 사항 및 이슈 (주의요망)')).toBeInTheDocument();
  expect(screen.getByText('구축 범위')).toBeInTheDocument();
  expect(screen.getByText('인원별 지원 횟수 / MD')).toBeInTheDocument();
  expect(screen.getByText('프로젝트 재시작')).toBeInTheDocument();
  expect(popupButton).toBeInTheDocument();
});
