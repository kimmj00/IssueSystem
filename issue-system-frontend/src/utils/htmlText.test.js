import { decodeHtmlEntities, stripHtml } from './htmlText';

describe('htmlText', () => {
  test('HTML 특수문자를 일반 문자로 변환한다', () => {
    expect(decodeHtmlEntities('# 민제 공통 &gt; 검색어 &amp; 결과')).toBe(
        '# 민제 공통 > 검색어 & 결과'
    );
  });

  test('중복 인코딩된 HTML 특수문자도 복원한다', () => {
    expect(decodeHtmlEntities('&amp;gt; 검색어')).toBe('> 검색어');
  });

  test('HTML 태그와 개행을 제거하면서 특수문자를 복원한다', () => {
    expect(stripHtml('<p># 민제 공통 &gt; 검색어 분리</p><div>&gt; 통합검색</div>')).toBe(
        '# 민제 공통 > 검색어 분리 > 통합검색'
    );
  });

  test('이미지와 줄바꿈 공백을 목록 표시용 공백으로 정리한다', () => {
    expect(stripHtml('앞&nbsp;<img src="data:image/png;base64,test"><br>뒤')).toBe('앞 뒤');
  });
});
