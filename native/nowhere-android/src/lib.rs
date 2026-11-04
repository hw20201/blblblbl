use anyhow::{Context, Result, anyhow};
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jint;
use nowhere::{Logger, Vector};
use tokio::runtime::Builder;
use url::Url;

/// Runs the official NW3 Vector runtime on the calling Android service thread.
/// The Kotlin service binds its own process to a physical Android Network before
/// invoking this entry point, so Rust sockets share that process binding.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_arca_android_service_NowhereNative_run(
    mut env: JNIEnv,
    _class: JClass,
    raw_url: JString,
) -> jint {
    let raw_url: String = match env.get_string(&raw_url) {
        Ok(value) => value.into(),
        Err(error) => {
            let _ = env.throw_new(
                "java/lang/IllegalArgumentException",
                format!("Nowhere link could not be read: {error}"),
            );
            return -1;
        }
    };

    match run_vector(&raw_url) {
        Ok(()) => 0,
        Err(error) => {
            let _ = env.throw_new(
                "java/lang/IllegalStateException",
                format!("Nowhere Vector stopped: {error:#}"),
            );
            -1
        }
    }
}

fn run_vector(raw_url: &str) -> Result<()> {
    let parsed = Url::parse(raw_url).context("invalid Vector URL")?;
    if parsed.scheme() != "vector" {
        return Err(anyhow!(
            "only canonical vector:// runtime URLs are accepted"
        ));
    }
    let vector = Vector::new(parsed, Logger)?;
    let runtime = Builder::new_multi_thread()
        .enable_all()
        .thread_name("Nowhere-Vector")
        .build()
        .context("failed to create Tokio runtime")?;
    runtime.block_on(vector.run())
}
