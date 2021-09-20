package zone.cogni.asquare.transactional;


import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class ASquareTransactionalAspect {

  private final ASquareTransaction aSquareTransaction;

  public ASquareTransactionalAspect(ASquareTransaction aSquareTransaction) {
    this.aSquareTransaction = aSquareTransaction;
  }

  @Around("@annotation(transactionAnnotation)")
  public Object around(ProceedingJoinPoint pjp, ASquareTransactional transactionAnnotation) throws Throwable {
    return aSquareTransaction.transactChecked(pjp::proceed, transactionAnnotation.value());
  }

}
