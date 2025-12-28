1. 如何设计`notifyEventStart`的可变参数？数组？对象？数组的话我怎么知道每个参数的含义呢？
2. 只能单向发送事件，那我怎么做好转发呢？
3. 如果做一个TranPerfEventDispatcher.java这需要成为一个systemservice的子类了，vendor层有什么事件需要我来上报吗？还是说system报system的，vendor报vendor的，这样不影响呢？