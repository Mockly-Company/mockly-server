package app.mockly.domain.interview.service;

import app.mockly.domain.auth.service.TokenBlacklistService;
import app.mockly.domain.interview.dto.InterviewFeedbackResult;
import app.mockly.domain.interview.entity.InterviewMessage;
import app.mockly.domain.interview.entity.InterviewMessageRole;
import app.mockly.domain.interview.entity.InterviewType;
import app.mockly.domain.product.entity.PlanTier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("latency")
@Tag("latency")
class AiFeedbackLatencyTest {

    @Autowired
    private InterviewAiService interviewAiService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    private static final int WARMUP = 10;
    private static final int MEASUREMENT = 100;

    @Test
    void measureGenerateFeedbackLatency() {
        List<List<InterviewMessage>> samples = createAllSamples();
        List<Long> durations = new ArrayList<>();

        for (int i = 0; i < WARMUP + MEASUREMENT; i++) {
            List<InterviewMessage> history = samples.get(i % samples.size());
            long start = System.nanoTime();
            InterviewFeedbackResult result = interviewAiService.generateFeedback(
                    history, InterviewType.TECHNICAL, PlanTier.PRO);
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            if (i < WARMUP) {
                System.out.printf("[warmup %2d] %,d ms%n", i + 1, elapsed);
            } else {
                durations.add(elapsed);
                System.out.printf("[%3d/%d] %,d ms | score=%d experts=%d%n",
                        i - WARMUP + 1, MEASUREMENT, elapsed,
                        result.overallScore(), result.expertFeedbacks().size());
            }
        }

        Collections.sort(durations);
        double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0);

        System.out.println("\n========== generateFeedback() Latency Report ==========");
        System.out.printf("  Samples : %d (warmup: %d)%n", MEASUREMENT, WARMUP);
        System.out.printf("  Avg     : %,.0f ms%n", avg);
        System.out.printf("  p50     : %,d ms%n", percentile(durations, 50));
        System.out.printf("  p90     : %,d ms%n", percentile(durations, 90));
        System.out.printf("  p95     : %,d ms%n", percentile(durations, 95));
        System.out.printf("  p99     : %,d ms%n", percentile(durations, 99));
        System.out.printf("  Max     : %,d ms%n", durations.get(durations.size() - 1));
        System.out.println("=======================================================");

        assertThat(durations).isNotEmpty();
    }

    private List<List<InterviewMessage>> createAllSamples() {
        return List.of(
                backendSample1(),
                backendSample2(),
                backendSample3(),
                frontendSample1(),
                frontendSample2(),
                frontendSample3(),
                plannerSample(),
                marketerSample()
        );
    }

    // ========== 백엔드 샘플 3개 ==========

    private List<InterviewMessage> backendSample1() {
        return List.of(
                msg(InterviewMessageRole.INTERVIEWER, 1,
                        "Spring Boot에서 의존성 주입(DI)이란 무엇이고, 어떤 방식으로 동작하는지 설명해주세요."),
                msg(InterviewMessageRole.USER, 1,
                        "의존성 주입은 객체가 필요로 하는 의존 객체를 직접 생성하지 않고 외부에서 주입받는 패턴입니다. " +
                        "Spring에서는 IoC 컨테이너가 Bean을 관리하며, 생성자 주입, 세터 주입, 필드 주입 방식이 있습니다. " +
                        "생성자 주입이 권장되는 이유는 불변성을 보장하고 순환 참조를 컴파일 타임에 발견할 수 있기 때문입니다."),
                msg(InterviewMessageRole.INTERVIEWER, 2,
                        "순환 참조가 발생하면 Spring은 어떻게 처리하나요? 실제로 경험해보신 적이 있으신가요?"),
                msg(InterviewMessageRole.USER, 2,
                        "생성자 주입에서 순환 참조가 발생하면 BeanCurrentlyInCreationException이 발생합니다. " +
                        "프로젝트에서 Service A와 Service B가 서로 의존하는 상황이 있었는데, " +
                        "공통 로직을 별도 서비스로 분리해서 해결했습니다. @Lazy를 쓸 수도 있지만 구조적으로 해결하는 것이 낫다고 판단했습니다."),
                msg(InterviewMessageRole.INTERVIEWER, 3,
                        "@Lazy와 구조적 분리 외에 순환 참조를 해결하는 다른 방법은 무엇이 있을까요?"),
                msg(InterviewMessageRole.USER, 3,
                        "이벤트 기반 처리로 의존성을 끊을 수 있습니다. ApplicationEventPublisher를 사용해서 " +
                        "한쪽이 이벤트를 발행하고 다른 쪽이 리스너로 처리하면 직접 의존이 사라집니다. " +
                        "또한 인터페이스를 도입해서 의존 방향을 역전시키는 DIP 적용도 방법입니다.")
        );
    }

    private List<InterviewMessage> backendSample2() {
        return List.of(
                msg(InterviewMessageRole.INTERVIEWER, 1,
                        "대규모 트래픽 환경에서 데이터베이스 조회 성능을 개선하기 위해 어떤 전략을 사용해보셨나요?"),
                msg(InterviewMessageRole.USER, 1,
                        "주문 조회 API에서 N+1 문제가 발생해서 응답 시간이 2초 이상 걸리는 상황이 있었습니다. " +
                        "JPA의 fetch join으로 쿼리를 줄이고, 자주 조회되는 상품 정보는 Redis 캐시를 적용했습니다. " +
                        "DB 인덱스도 복합 인덱스로 재설계해서 최종적으로 응답 시간을 200ms 이하로 줄였습니다. " +
                        "추가로 읽기 전용 레플리카를 도입해서 조회 트래픽을 분산시켰습니다."),
                msg(InterviewMessageRole.INTERVIEWER, 2,
                        "Redis 캐시를 적용할 때 캐시 무효화(invalidation)는 어떻게 처리하셨나요?"),
                msg(InterviewMessageRole.USER, 2,
                        "상품 정보 수정 시 Cache-Aside 패턴으로 해당 키를 삭제하고 다음 조회 시 DB에서 다시 로드했습니다. " +
                        "TTL은 5분으로 설정해서 최악의 경우에도 5분 뒤에는 최신 데이터가 반영됩니다. " +
                        "캐시 스탬피드 문제를 방지하기 위해 분산 락을 적용할지 고민했지만, " +
                        "해당 데이터의 갱신 빈도가 낮아서 TTL 기반으로 충분했습니다."),
                msg(InterviewMessageRole.INTERVIEWER, 3,
                        "캐시 스탬피드가 발생하면 구체적으로 어떤 문제가 생기고, 어떻게 대응하시겠어요?"),
                msg(InterviewMessageRole.USER, 3,
                        "TTL 만료 직후 다수의 요청이 동시에 DB를 조회해서 부하가 급증하는 현상입니다. " +
                        "Redisson의 분산 락으로 하나의 요청만 DB를 조회하게 하거나, " +
                        "TTL 만료 전에 미리 갱신하는 사전 로딩(pre-warming) 방식이 있습니다.")
        );
    }

    private List<InterviewMessage> backendSample3() {
        return List.of(
                msg(InterviewMessageRole.INTERVIEWER, 1,
                        "MSA 환경에서 서비스 간 트랜잭션 일관성을 어떻게 보장하셨나요?"),
                msg(InterviewMessageRole.USER, 1,
                        "잘 모르겠습니다. 아직 MSA를 직접 경험해보지는 못했습니다."),
                msg(InterviewMessageRole.INTERVIEWER, 2,
                        "그러면 모놀리식 환경에서 여러 서비스의 트랜잭션을 관리한 경험이 있으신가요?"),
                msg(InterviewMessageRole.USER, 2,
                        "@Transactional을 사용해서 주문과 결제를 하나의 트랜잭션으로 묶었습니다. " +
                        "결제 실패 시 주문도 롤백되도록 했습니다."),
                msg(InterviewMessageRole.INTERVIEWER, 3,
                        "외부 결제 API 호출이 트랜잭션 안에 있으면 어떤 문제가 발생할 수 있을까요?"),
                msg(InterviewMessageRole.USER, 3,
                        "음... 외부 API가 느리면 트랜잭션이 오래 유지되는 문제가 있을 것 같습니다. " +
                        "DB 커넥션을 오래 점유하게 되니까요. 근데 구체적으로 어떻게 해결하는지는 잘 모르겠습니다.")
        );
    }

    // ========== 프론트엔드 샘플 3개 ==========

    private List<InterviewMessage> frontendSample1() {
        return List.of(
                msg(InterviewMessageRole.INTERVIEWER, 1,
                        "React에서 상태 관리를 할 때 useState와 useReducer를 어떤 기준으로 선택하시나요?"),
                msg(InterviewMessageRole.USER, 1,
                        "단순한 상태는 useState를 사용하고, 여러 하위 값이 연관되어 있거나 상태 전이 로직이 복잡한 경우 useReducer를 씁니다. " +
                        "예를 들어 폼 데이터에서 필드 간 유효성 검증이 연쇄적으로 필요할 때 useReducer가 적합했습니다. " +
                        "action 타입으로 상태 변화를 명시적으로 표현할 수 있어서 디버깅에도 유리합니다."),
                msg(InterviewMessageRole.INTERVIEWER, 2,
                        "전역 상태 관리 도구로는 무엇을 사용해보셨고, 선택 기준은 무엇이었나요?"),
                msg(InterviewMessageRole.USER, 2,
                        "Redux Toolkit과 Zustand를 모두 사용해봤습니다. 대규모 프로젝트에서는 RTK가 미들웨어와 DevTools 생태계가 잘 되어 있어서 선택했고, " +
                        "소규모 프로젝트에서는 Zustand가 보일러플레이트가 적어서 생산성이 높았습니다. " +
                        "서버 상태는 TanStack Query로 분리해서 클라이언트 상태와 서버 상태를 명확히 구분합니다."),
                msg(InterviewMessageRole.INTERVIEWER, 3,
                        "TanStack Query의 staleTime과 gcTime 설정은 어떤 기준으로 정하셨나요?"),
                msg(InterviewMessageRole.USER, 3,
                        "데이터 특성에 따라 다르게 설정합니다. 사용자 프로필처럼 변경이 드문 데이터는 staleTime을 5분으로, " +
                        "실시간성이 중요한 알림 목록은 0으로 설정합니다. " +
                        "gcTime은 기본 5분을 유지하되, 메모리 이슈가 관측되면 조정합니다.")
        );
    }

    private List<InterviewMessage> frontendSample2() {
        return List.of(
                msg(InterviewMessageRole.INTERVIEWER, 1,
                        "웹 성능 최적화를 위해 어떤 작업을 해보셨나요?"),
                msg(InterviewMessageRole.USER, 1,
                        "Lighthouse 점수가 40점대였던 프로젝트에서 이미지 최적화, 코드 스플리팅, 폰트 프리로드를 적용했습니다. " +
                        "Next.js의 Image 컴포넌트로 WebP 변환과 lazy loading을 적용했고, " +
                        "dynamic import로 초기 번들 크기를 60% 줄였습니다. " +
                        "CLS 개선을 위해 이미지에 width/height를 명시하고, 스켈레톤 UI를 도입했습니다. " +
                        "최종적으로 Lighthouse 점수를 92점까지 올렸습니다."),
                msg(InterviewMessageRole.INTERVIEWER, 2,
                        "코드 스플리팅을 적용할 때 청크 크기는 어떤 기준으로 나누셨나요?"),
                msg(InterviewMessageRole.USER, 2,
                        "라우트 단위로 기본 스플리팅하고, 큰 라이브러리(차트, 에디터)는 별도 청크로 분리했습니다. " +
                        "Webpack Bundle Analyzer로 확인하면서 200KB 이상인 청크를 찾아 추가로 분리했습니다."),
                msg(InterviewMessageRole.INTERVIEWER, 3,
                        "SSR과 CSR 중 어떤 기준으로 렌더링 전략을 결정하시나요?"),
                msg(InterviewMessageRole.USER, 3,
                        "SEO가 필요한 페이지(상품 상세, 블로그)는 SSR 또는 SSG를 적용합니다. " +
                        "대시보드처럼 인증이 필요하고 SEO가 불필요한 페이지는 CSR로 처리합니다. " +
                        "ISR은 상품 목록처럼 데이터가 주기적으로 변하지만 실시간은 아닌 경우에 사용합니다.")
        );
    }

    private List<InterviewMessage> frontendSample3() {
        return List.of(
                msg(InterviewMessageRole.INTERVIEWER, 1,
                        "접근성(a11y)을 고려한 개발 경험이 있으신가요?"),
                msg(InterviewMessageRole.USER, 1,
                        "네, WAI-ARIA 속성을 적용하고 키보드 네비게이션을 지원하도록 개발했습니다."),
                msg(InterviewMessageRole.INTERVIEWER, 2,
                        "구체적으로 어떤 컴포넌트에서 어떤 ARIA 속성을 사용하셨나요?"),
                msg(InterviewMessageRole.USER, 2,
                        "드롭다운 메뉴에서 aria-expanded, aria-haspopup을 사용했고, " +
                        "모달에는 aria-modal, role='dialog'를 적용했습니다. " +
                        "탭 컴포넌트에서는 role='tablist', 'tab', 'tabpanel'을 사용해서 " +
                        "스크린 리더가 탭 구조를 인식하도록 했습니다."),
                msg(InterviewMessageRole.INTERVIEWER, 3,
                        "접근성 테스트는 어떤 방식으로 진행하셨나요?"),
                msg(InterviewMessageRole.USER, 3,
                        "axe-core 기반의 jest-axe로 자동화 테스트를 작성하고, " +
                        "실제 VoiceOver와 NVDA로 수동 테스트를 병행했습니다. " +
                        "Lighthouse 접근성 점수도 CI에서 90점 이상을 유지하도록 설정했습니다.")
        );
    }

    // ========== 기획자 / PM / 마케터 샘플 ==========

    private List<InterviewMessage> plannerSample() {
        return List.of(
                msg(InterviewMessageRole.INTERVIEWER, 1,
                        "신규 기능을 기획할 때 우선순위를 어떤 기준으로 정하시나요?"),
                msg(InterviewMessageRole.USER, 1,
                        "RICE 프레임워크를 주로 사용합니다. Reach, Impact, Confidence, Effort를 점수화해서 비교합니다. " +
                        "하지만 RICE만으로는 전략적 방향성을 놓칠 수 있어서, OKR과 연결해서 상위 목표에 기여하는지를 먼저 판단합니다. " +
                        "데이터가 부족한 초기 기능은 유저 인터뷰 결과를 Confidence 산정에 반영합니다."),
                msg(InterviewMessageRole.INTERVIEWER, 2,
                        "개발팀과 일정 협의 시 의견이 충돌하면 어떻게 조율하시나요?"),
                msg(InterviewMessageRole.USER, 2,
                        "기술적 제약을 충분히 이해하려고 노력합니다. 개발 리드와 1:1로 먼저 대화해서 " +
                        "어떤 부분이 기술적으로 어렵고 왜 그런지 파악합니다. " +
                        "그 후 MVP 범위를 조정하거나, 페이즈를 나눠서 핵심 기능만 먼저 배포하는 방식으로 타협점을 찾습니다. " +
                        "중요한 건 일방적으로 밀어붙이지 않고 상호 합의된 마일스톤을 만드는 것입니다."),
                msg(InterviewMessageRole.INTERVIEWER, 3,
                        "기획한 기능이 출시 후 기대한 성과를 내지 못했던 경험이 있나요? 어떻게 대응하셨나요?"),
                msg(InterviewMessageRole.USER, 3,
                        "소셜 공유 기능을 추가했는데 사용률이 2%도 안 됐습니다. " +
                        "유저 행동 데이터를 분석해보니 공유 버튼 위치가 너무 깊었고, 공유할 동기도 부족했습니다. " +
                        "A/B 테스트로 버튼 위치를 변경하고 공유 시 포인트 리워드를 추가한 결과 12%까지 올릴 수 있었습니다.")
        );
    }

    private List<InterviewMessage> marketerSample() {
        return List.of(
                msg(InterviewMessageRole.INTERVIEWER, 1,
                        "디지털 마케팅 캠페인을 설계할 때 어떤 프로세스를 따르시나요?"),
                msg(InterviewMessageRole.USER, 1,
                        "먼저 캠페인 목표를 명확히 정의합니다. 인지도인지 전환인지에 따라 채널과 메시지가 완전히 달라지기 때문입니다. " +
                        "타겟 오디언스를 세그먼트하고, 채널별 크리에이티브를 준비한 뒤 A/B 테스트를 설계합니다. " +
                        "런칭 후에는 일별로 핵심 지표(CTR, CPA, ROAS)를 모니터링하며 실시간으로 예산을 재배분합니다."),
                msg(InterviewMessageRole.INTERVIEWER, 2,
                        "퍼포먼스 마케팅에서 ROAS가 목표 이하일 때 어떻게 개선하셨나요?"),
                msg(InterviewMessageRole.USER, 2,
                        "Facebook Ads에서 ROAS 1.5 목표 대비 0.8이 나왔던 경험이 있습니다. " +
                        "퍼널별로 분석해보니 랜딩 페이지 이탈률이 70%로 높았습니다. " +
                        "크리에이티브와 랜딩 메시지의 불일치가 원인이었고, " +
                        "랜딩 페이지를 광고 소재와 일치시키고 CTA를 강화한 결과 ROAS 2.1까지 개선했습니다."),
                msg(InterviewMessageRole.INTERVIEWER, 3,
                        "마케팅 성과를 비마케팅 팀(경영진, 개발팀)에 어떻게 커뮤니케이션하시나요?"),
                msg(InterviewMessageRole.USER, 3,
                        "경영진에게는 비즈니스 임팩트 중심으로 보고합니다. CTR이나 CPC 같은 채널 지표보다 " +
                        "매출 기여, CAC, LTV 같은 비즈니스 지표를 앞에 놓습니다. " +
                        "개발팀에게는 UTM 파라미터 설계나 이벤트 트래킹 요구사항을 구체적인 스펙으로 전달합니다. " +
                        "주간 리포트는 대시보드 링크와 핵심 인사이트 3줄로 요약해서 공유합니다.")
        );
    }

    private long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private InterviewMessage msg(InterviewMessageRole role, int questionNumber, String content) {
        return InterviewMessage.builder()
                .role(role)
                .content(content)
                .questionNumber(questionNumber)
                .build();
    }
}
