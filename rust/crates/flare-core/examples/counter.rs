use flare_core::presenter::CounterPresenter;

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let (presenter, task) = CounterPresenter::new(0);
    let task = tokio::spawn(task);
    let states = presenter.subscribe();

    println!("initial: {:?}", states.next_state().await);
    presenter.increment().await?;
    println!("increment: {:?}", states.next_state().await);
    presenter.decrement().await?;
    println!("decrement: {:?}", states.next_state().await);
    presenter.increment().await?;
    presenter.reset().await?;
    println!("latest: {:?}", states.next_state().await);

    states.stop();
    presenter.shutdown().await;
    task.await?;
    Ok(())
}
