import { render, screen } from '@testing-library/react';
import App from './App';

test('로그인하지 않은 사용자는 로그인 화면으로 진입한다', () => {
  sessionStorage.clear();
  localStorage.clear();
  render(<App />);
  expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: 'ID' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '회원가입' })).toBeInTheDocument();
});
